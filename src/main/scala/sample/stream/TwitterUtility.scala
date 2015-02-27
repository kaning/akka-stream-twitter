package sample.stream

import twitter4j._
import twitter4j.auth.AccessToken
import twitter4j.conf.ConfigurationBuilder
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._
import scala.util.Try
import scala.collection._
import akka.actor.ActorSystem

object CretentialsUtils {
  val config = ConfigFactory.load()
  val appKey: String = config.getString("appKey")
  val appSecret: String = config.getString("appSecret")
  val accessToken: String = config.getString("accessToken")
  val accessTokenSecret: String = config.getString("accessTokenSecret")
}

object TwitterClient {

  def apply(): Twitter = {
    val factory = new TwitterFactory(new ConfigurationBuilder().build())
    val t = factory.getInstance()
    t.setOAuthConsumer(CretentialsUtils.appKey, CretentialsUtils.appSecret)
    t.setOAuthAccessToken(new AccessToken(CretentialsUtils.accessToken, CretentialsUtils.accessTokenSecret))
    t
  }
}

class TwitterStreamClient(val actorSystem: ActorSystem) {
  val factory = new TwitterStreamFactory(new ConfigurationBuilder().build())
  val twitterStream = factory.getInstance()
  def init = {
    twitterStream.setOAuthConsumer(CretentialsUtils.appKey, CretentialsUtils.appSecret)
    twitterStream.setOAuthAccessToken(new AccessToken(CretentialsUtils.accessToken, CretentialsUtils.accessTokenSecret))
    twitterStream.addListener(simpleStatusListener)
    twitterStream.sample
  }

  def simpleStatusListener = new StatusListener() {
    def onStatus(status: Status) { actorSystem.eventStream.publish(status) }
    def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) {}
    def onTrackLimitationNotice(numberOfLimitedStatuses: Int) {}
    def onException(ex: Exception) { ex.printStackTrace }
    def onScrubGeo(arg0: Long, arg1: Long) {}
    def onStallWarning(warning: StallWarning) {}
  }

  def stop = {
    twitterStream.cleanUp
    twitterStream.shutdown
  }

}

object TwitterHelpers {
  // Lookup user profiles in batches of 100
  def lookupUsers(ids: List[Long]): List[User] = {
    val client = TwitterClient()
    val res = client.lookupUsers(ids.toArray)
    res.asScala.toList
  }

  // Fetch the IDs of a user's followers in batches of 5000
  def getFollowers(userId: Long): Try[Set[Long]] = {
    Try({
      val followerIds = mutable.Set[Long]()
      var cursor = -1L
      do {
        val client = TwitterClient()
        val res = client.friendsFollowers().getFollowersIDs(userId, cursor, 5000)
        res.getIDs.toList.foreach(x => followerIds.add(x))
        if (res.hasNext) {
          cursor = res.getNextCursor
        } else {
          cursor = -1 // Exit the loop
        }
      } while (cursor > 0)
      followerIds.toSet
    })
  }
}