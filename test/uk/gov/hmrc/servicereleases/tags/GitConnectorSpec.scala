/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.servicedeployments.tags

import java.time.ZonedDateTime
import java.util.Date

import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.test.FakeApplication
import play.api.test.Helpers._
import uk.gov.hmrc.BlockingIOExecutionContext
import uk.gov.hmrc.gitclient.{GitClient, GitTag}
import uk.gov.hmrc.githubclient.{GhRepoRelease, GithubApiClient}

import scala.concurrent.Future


class GitConnectorSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures {
  val gitClient = mock[GitClient]
  val gitHubClient = mock[GithubApiClient]
  val connector = new GitConnector(gitClient, gitHubClient, "")

  "getGitRepoTags" should {

    "return tags form gitClient with normalized tag name (i.e just the numbers)" in running(FakeApplication()) {
      val now = ZonedDateTime.now()
      val repoName = "repoName"
      val org = "HMRC"

      when(gitClient.getGitRepoTags("repoName", "HMRC")(BlockingIOExecutionContext.executionContext))
        .thenReturn(Future.successful(List(
          GitTag("v1.0.0", Some(now)),
          GitTag("deployment/9.101.0", Some(now)),
          GitTag("someRandomtagName", Some(now)))))

      connector.get(org, repoName).futureValue shouldBe List(
        Tag("1.0.0", now.toLocalDateTime),
        Tag("9.101.0", now.toLocalDateTime),
        Tag("someRandomtagName", now.toLocalDateTime))
    }

    "try to lookup tag dates from the github deployments if tag date is missing and only return tags which have dates" in running(FakeApplication()) {
      val now = ZonedDateTime.now()
      val repoName = "repoName"
      val org = "HMRC"

      when(gitHubClient.getReleases("HMRC", "repoName")(BlockingIOExecutionContext.executionContext)).thenReturn(
        Future.successful(List(
          GhRepoRelease(123, "someRandomTagName", Date.from(now.toInstant)),
          GhRepoRelease(124, "deployment/9.102.0", Date.from(now.toInstant)))))

      when(gitClient.getGitRepoTags("repoName", "HMRC")(BlockingIOExecutionContext.executionContext))
        .thenReturn(Future.successful(List(
          GitTag("v1.0.0", None),
          GitTag("deployment/9.101.0", Some(now)),
          GitTag("deployment/9.102.0", None),
          GitTag("someRandomTagName", None))))

      connector.get(org, repoName).futureValue shouldBe List(
        Tag("9.101.0", now.toLocalDateTime),
        Tag("9.102.0", now.toLocalDateTime),
        Tag("someRandomTagName", now.toLocalDateTime))
    }

  }
}
