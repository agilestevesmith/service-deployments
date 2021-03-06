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

import java.time.LocalDateTime

import play.api.Logger

import scala.concurrent.Future
import scala.util.Try
import RepoType.{Enterprise, Open}
import uk.gov.hmrc.servicedeployments.FutureHelpers._

case class ServiceDeploymentTag(name: String, createdAt: LocalDateTime)

trait TagsService {
  def get(org: String, name: String, repoType: String): Future[Try[Seq[Tag]]]
}

class DefaultTagsService(gitEnterpriseTagDataSource: TagsDataSource, gitOpenTagDataSource: TagsDataSource)
  extends TagsService {

  def get(org: String, name: String, repoType: String) =
    RepoType.from(repoType) match {
      case Enterprise =>
        Logger.debug(s"$name org : $org get Enterprise Repo deployment tags")
        continueOnError(gitEnterpriseTagDataSource.get(org, name))

      case Open =>
        Logger.debug(s"$name org : $org get Open Repo deployment tags")
        continueOnError(gitOpenTagDataSource.get(org, name))
    }
}
