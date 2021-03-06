package com.htvu.instamua.rest.api.services

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.htvu.instamua.rest.api.JsonFormats
import com.htvu.instamua.rest.dao._
import com.htvu.instamua.rest.util.ActorExecutionContextProvider
import reactivemongo.core.commands.LastError
import spray.routing.Directives


class ListingService()(implicit system: ActorSystem) extends Directives with JsonFormats {

  import ListingActor._

  val listingActor = system.actorOf(ListingActor.props(), "listing-actor")
  implicit val ec = system.dispatcher

  import scala.concurrent.duration._
  implicit val timeout = Timeout(2.seconds)

  val routes = pathPrefix("listings") {
    pathEnd {
      post {
        handleWith { listing: Listing =>
          (listingActor? NewListing(listing)).toResponse[LastError]
        }
      }
    } ~
    path("search") {
      get {
        parameter('q.?, 'page.as[Int] ? 0) { (query, page) =>
          _ complete (listingActor? SearchListing(query, page)).toResponse[List[Listing]]
        }
      }
    } ~
    pathPrefix(Segment) { listingId =>
      pathEnd {
        get {
          _ complete (listingActor? GetListing(listingId)).toResponse[Option[Listing]]
        } ~
        put {
          handleWith { updated: ListingDetail =>
            (listingActor? UpdateListing(listingId, updated)).toResponse[LastError]
          }
        } ~
        delete {
          _ complete (listingActor? DeleteListing(listingId)).toResponse[LastError]
        }
      } ~
      pathPrefix("likes") {
        pathEnd {
          get {
            _ complete (listingActor ? GetLikes(listingId)).toResponse[Option[List[Like]]]
          } ~
          post {
            handleWith { comment: Comment =>
              ???
            }
          }
        } ~
        path(Segment) { likeId =>
          delete {
            _ complete (listingActor ? UnLike(listingId, likeId)).toResponse[LastError]
          }
        }
      }
    }
  }
}


object ListingActor {
  case class NewListing(listing: Listing)
  case class GetListing(listingId: String)
  case class UpdateListing(listingId: String, updated: ListingDetail)
  case class DeleteListing(listingId: String)

  case class GetLikes(listingId: String)
  case class NewLike(listingId: String, like: Like)
  case class UnLike(listingId: String, likeId: String)

  case class SearchListing(query: Option[String], page: Int)

  def props(): Props = Props(new ListingActor())
}

class ListingActor extends Actor with ListingDAO with ActorExecutionContextProvider{
  import ListingActor._

  def receive: Receive = {
    case NewListing(listing) =>
      createNewListing(listing) pipeTo sender
    case GetListing(listingId) =>
      getListing(listingId) pipeTo sender
    case UpdateListing(listingId, updated) =>
      updateListing(listingId, updated) pipeTo sender
    case DeleteListing(listingId) =>
      deleteListing(listingId) pipeTo sender
    case GetLikes(listingId) =>
      getLikes(listingId) pipeTo sender
    case NewLike(listingId, like) =>
      createNewLike(listingId, like) pipeTo sender
    case UnLike(listingId, likeId) =>
      unlike(listingId, likeId) pipeTo sender
    case SearchListing(query, page) =>
      search(query, page) pipeTo sender
  }
}



