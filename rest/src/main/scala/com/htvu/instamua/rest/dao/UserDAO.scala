package com.htvu.instamua.rest.dao

import com.htvu.instamua.rest.dao.Relationship._
import com.typesafe.scalalogging.LazyLogging
import slick.driver.MySQLDriver.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class UserDAO extends LazyLogging {
  val db:Database = Database.forConfig("jdbc")
  
  val users = TableQuery[Users]
  val userPrivateInfos = TableQuery[UserPrivateInfos]
  val userCredentials = TableQuery[UserCredentials]
  val followers = TableQuery[Followers]

  def createNewUser(info: UserRegistrationInfo): Future[Int] = {
      db.run(users.returning(users.map(_.id)) += User(-1, info.username, Some(info.fullName), Some(info.location), None, None, None, None, Some(info.ssoId)))
  }

  def getUserInfo(userId: Int): Future[Option[User]] = {
    db.run(users.filter(_.id === userId).take(1).result.headOption)
  }

  //get user info based on SSO userid
  def getUserInfoSSO(ssoId: Int): Future[Option[User]] = {
    db.run(users.filter(_.ssoId === ssoId).take(1).result.headOption)
  }

  def getUserPrivateInfo(userId: Int): Future[Option[UserPrivateInfo]] =
    db.run(userPrivateInfos.filter(_.userId === userId).take(1).result.headOption)

  def updateUserInfo(newInfo: User): Future[Try[Int]] = {
    val updateUserInfo = users.filter(_.id === newInfo.id)
    db.run(updateUserInfo.update(newInfo).asTry)
  }

  def updateUserPrivateInfo(newPrivateInfo: UserPrivateInfo): Future[Try[Int]] = {
    val updateUserPrivateInfo = userPrivateInfos.filter(_.userId === newPrivateInfo.userId)
    db.run(updateUserPrivateInfo.update(newPrivateInfo).asTry)
  }

  def searchUser(query: String, page: Int = 0, limit: Int = 10): Future[Seq[UserSearchResult]] = {
        db.run(sql"""select user_id, username, full_name, profile_picture
               from user where username like '%#$query%' or full_name like '%#$query%'
               order by user_id limit #${page*limit},#$limit""".as[UserSearchResult])

    // TODO: replace sql with the implementation below
//    val q = for {
//      user <- users if (user.username like s"%$query%") || (user.fullName like s"%$query%")
//    } yield (user.id, user.username, user.fullName, user.profilePicture)
//    println(q.result.statements)
//    db.run(q.result)
  }

  // RelationshipService
  def getFollowers(a: Int, page: Int = 0, limit: Int = 10): Future[Seq[FollowerListResult]] = {
    val fs = followers filter (f => f.userId === a) map (f => (f.followerId, f.followBack)) union
            (followers filter (f => f.followerId === a && f.followBack) map (f => (f.userId, f.followBack)))
    val query = for {
      f <- fs
      u <- users if u.id === f._1
    } yield (u.id, u.username, u.fullName.?, u.profilePicture.?, f._2)

    db.run(query drop(page*limit) take(limit) sortBy(_._1) result)
  }

  def getFollowings(a: Int, page: Int = 0, limit: Int = 10): Future[Seq[FollowerListResult]] = {
    val fs = followers filter (f => f.followerId === a) map (f => (f.userId, f.followBack)) union
      (followers filter(f => f.userId === a && f.followBack) map (f => (f.followerId, f.followBack)))
    val query = for {
      f <- fs
      u <- users if u.id === f._1
    } yield (u.id, u.username, u.fullName.?, u.profilePicture.?, f._2)
    db.run(query drop(page*limit) take(limit) sortBy(_._1) result)
  }

  def getRelationship(a: Int, b: Int)(implicit exec: ExecutionContext): Future[Relationship] =
    db.run(followers.filter(f => (f.userId === a && f.followerId === b) ||
      (f.userId === b && f.followerId === a)).take(1).result.headOption
    ) map {
      case Some(f) =>
        if (f.userId == a && f.followerId == b)
          if (f.followBack) B_TO_A_BOTH else B_TO_A
        else
          if (f.followBack) A_TO_B_BOTH else  A_TO_B
      case None => NO_REL
    }

  def postRelationship(a: Int, b: Int, currentRel: Relationship)(implicit exec: ExecutionContext): Future[Int] =
    if (currentRel == B_TO_A)
      db.run(followers.filter(u => u.userId === a && u.followerId === b) map
        (f => (f.userId, f.followerId, f.followBack)) update ((a, b, true)))
    else if (currentRel == B_TO_A_BOTH)
      db.run(followers.filter(u => u.userId === a && u.followerId === b) map
        (f => (f.userId, f.followerId, f.followBack)) update ((a, b, false)))
    else if (currentRel == A_TO_B)
      db.run(followers.filter(u => u.userId === b && u.followerId === a) delete)
    else if (currentRel == A_TO_B_BOTH)
      db.run(followers.filter(u => u.userId === b && u.followerId === a) map
        (f => (f.userId, f.followerId, f.followBack)) update ((a, b, false)))
    else db.run(DBIO.seq(followers += Follower(b, a, followBack = false))) map(f => 1)
}

object UserDAO extends UserDAO
