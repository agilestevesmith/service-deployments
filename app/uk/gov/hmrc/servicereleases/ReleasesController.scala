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

import java.time.{Period, LocalDateTime}

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.Action
import play.modules.reactivemongo.MongoDbConnection

import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.servicereleases.Release
import uk.gov.hmrc.servicereleases.deployments.{Deployment, DeploymentsDataSource}

import scala.concurrent.Future
import scala.io.Source


case class ReleaseResult(name: String, version: String,
                         creationDate: Option[LocalDateTime], productionDate: LocalDateTime,
                         interval: Option[Long], leadTime: Option[Long])

object ReleaseResult {

  import uk.gov.hmrc.JavaDateTimeJsonFormatter._

  implicit val formats = Json.format[ReleaseResult]

  def fromRelease(release: Release): ReleaseResult = {
    ReleaseResult(
      release.name,
      release.version,
      release.creationDate,
      release.productionDate,
      release.interval,
      release.leadTime
    )
  }

}

object ReleasesController extends ReleasesController with MongoDbConnection {
  override def releasesRepository = new MongoReleasesRepository(db)
}

trait ReleasesController extends BaseController {

  import uk.gov.hmrc.JavaDateTimeJsonFormatter._

  def releasesRepository: ReleasesRepository

  def forService(serviceName: String) = Action.async { implicit request =>
    releasesRepository.getForService(serviceName).map {
      case Some(data) => Ok(Json.toJson(data.map(ReleaseResult.fromRelease)))
      case None => NotFound
    }
  }

  def getAll() = Action.async { implicit request =>
    releasesRepository.getAllReleases.map { releases =>
      Ok(Json.toJson(releases.map(ReleaseResult.fromRelease)))
    }

  }

  def update() = Action.async { implicit request =>
    Scheduler.run.map {
      case Info(message) => Ok(message)
      case Warn(message) => Ok(message)
      case Error(message) => InternalServerError(message)
    }
  }

  def importRaw() = Action.async(parse.temporaryFile) { request =>
    implicit val reads: Reads[Deployment] = (
      (JsPath \ "env").read[String] and
        (JsPath \ "an").read[String] and
        (JsPath \ "ver").read[String] and
        (JsPath \ "fs").read[LocalDateTime]
      )(Deployment.apply _)

    val source = Source.fromFile(request.body.file, "UTF-8")
    val jsons = for (line <- source.getLines()) yield Json.fromJson[Deployment](Json.parse(line))

    val scheduler = new Scheduler with DefaultSchedulerDependencies {
      val deploymentsDataSource = new DeploymentsDataSource {
        def getAll: Future[List[Deployment]] = Future.successful(jsons.map(_.get).toList)
      }
    }

    scheduler.run.map {
      case Info(message) => Ok(message)
      case Warn(message) => Ok(message)
      case Error(message) => InternalServerError(message)
    }
  }

  def clear() = Action.async { implicit request =>
    releasesRepository.clearAllData map { r =>
      Ok(r.toString)
    }
  }
}
