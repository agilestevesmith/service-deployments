/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.servicereleases

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import org.joda.time.Duration
import play.Logger
import play.libs.Akka
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.gitclient.Git
import uk.gov.hmrc.githubclient.GithubApiClient
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}
import uk.gov.hmrc.servicereleases.deployments.{DefaultServiceDeploymentsService, ReleasesApiConnector}
import uk.gov.hmrc.servicereleases.services.{CatalogueConnector, DefaultServiceRepositoriesService}
import uk.gov.hmrc.servicereleases.tags.{DefaultTagsService, GitConnector, GitHubConnector}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

sealed trait JobResult
case class Error(message: String) extends JobResult {
  Logger.error(message)
}
case class Warn(message: String) extends JobResult {
  Logger.warn(message)
}
case class Info(message: String) extends JobResult {
  Logger.info(message)
}

trait Scheduler extends LockKeeper with MongoDbConnection {
  def akkaSystem: ActorSystem
  def releasesService: ReleasesService

  override def repo: LockRepository = LockMongoRepository(db)
  override def lockId: String = "service-releases-scheduled-job"
  override val forceLockReleaseAfter: Duration = Duration.standardMinutes(15)

  def start(interval: FiniteDuration): Unit = {
    Logger.info(s"Initialising mongo update every $interval")

    akkaSystem.scheduler.schedule(FiniteDuration(1, TimeUnit.SECONDS), interval) {
      run
    }
  }

  def run: Future[JobResult] = {
    tryLock {
      Logger.info(s"Starting mongo update")

      releasesService.updateModel().map { result =>
        val total = result.toList.length
        val failureCount = result.count(r => !r)
        Info(s"Added ${total - failureCount} new releases and encountered $failureCount failures")
      }.recover { case ex =>
        Error(s"Something went wrong during the mongo update: ${ex.getMessage}")
      }
    } map { resultOrLocked =>
      resultOrLocked getOrElse {
        Warn("Failed to obtain lock. Another process may have it.")
      }
    }
  }
}

object Scheduler extends Scheduler {
  import ServiceReleasesConfig._

  val akkaSystem = Akka.system()

  val enterpriseDataSource = new GitConnector(
    Git(gitEnterpriseStorePath, gitEnterpriseToken, gitEnterpriseHost, withCleanUp = true),
    GithubApiClient(gitEnterpriseApiUrl, gitEnterpriseToken))

  val openDataSource = new GitHubConnector(GithubApiClient(gitOpenApiUrl, gitOpenToken))

  val releasesService = new DefaultReleasesService(
    new DefaultServiceRepositoriesService(new CatalogueConnector(catalogueBaseUrl)),
    new DefaultServiceDeploymentsService(new ReleasesApiConnector(releasesApiBase)),
    new DefaultTagsService(enterpriseDataSource, openDataSource),
    new MongoReleasesRepository(db)
  )
}
