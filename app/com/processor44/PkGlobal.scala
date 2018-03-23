package com.processor44

//import akka.actor.ActorPath
import akka.actor._
import com.processor44.tick.TickConsumer
import play.api._
//import play.api.libs.concurrent.Akka
import play.api.Play.current
import scala.concurrent.Future
import akka.{ NotUsed, Done }
import javax.inject._

//import akka.stream._
//import akka.stream.scaladsl._



/**
 * Created by markwilson on 3/13/15.
 */

class PkGlobal @Inject() (system: ActorSystem) {

  var pathTickConsumer = "/user/TickConsumer"
  var pathEventConsumer = "/user/EventConsumer"

  def onStartapp(): Unit = {
    //val a = Akka.system.actorOf(TickConsumer.propsTickConsumerActor, "TickConsumer")
    //val a = system.actorOf(TickConsumer.propsTickConsumerActor, "TickConsumer")

    val a = system.actorOf(TickConsumer.propsTickConsumerActor("event"), "EventConsumer")
   //val alter = system.actorOf(TickConsumer.propsTickConsumerActor("tick"), "TickConsumer")

    val testA = system.actorSelection(pathEventConsumer)
    testA ! TickConsumer.Test
  }

  def onStartDemo(): Unit = {
    val a = system.actorSelection(pathEventConsumer)
    //val alter = system.actorSelection(pathTickConsumer)

    a ! TickConsumer.Consume
    //alter ! TickConsumer.Consume
  }

  def onStop(): Unit = {
    println("Global.onStop")
	Logger.debug("PkGlobal.onStop")
    val a = system.actorSelection(pathTickConsumer)
    Logger.debug("PkGlobal.onStop.actorSelection")
	a ! TickConsumer.Test
	Logger.debug("PkGlobal.onStop.Test")
    a ! TickConsumer.Shutdown
	Logger.debug("PkGlobal.onStop.Shutdown")
	//val done: Future[Done] = CoordinatedShutdown(system).run(CoordinatedShutdown.UnknownReason)
    //system.shutdown()
    //system.awaitTermination()
  }


}
