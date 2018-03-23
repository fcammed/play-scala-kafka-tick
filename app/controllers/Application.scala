package controllers

import java.io.File
import com.processor44.models.{Msg, Tick, ViewModels}
import com.processor44.tick.{TickConsumer, TickProducer, TickSimpleConsumer}
import com.typesafe.config.{Config, ConfigFactory}
//import play.api._
import play.api.libs.EventSource
import play.api.libs.iteratee.{Concurrent, Enumeratee}
//import play.api.libs.streams.Streams  deprecated en 2.6
import play.api.libs.iteratee.streams.IterateeStreams
import play.api.libs.json.Json
//import play.api.libs.json.JsValue
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
//para migrar GlobalSettings
//onStart
import com.processor44.PkGlobal
//onStop
import javax.inject._
import play.api.inject.ApplicationLifecycle

//import akka.stream._
import akka.stream.scaladsl._

import com.vector.actors.PromotionEngine
import akka.actor._
import akka.util.Timeout
import com.vector.actors.PromotionEngine._
import play.api.Logger
//import scala.util.{Failure, Success}
import akka.routing.BalancingPool

import play.api.libs.ws._

@Singleton
class Application @Inject() (ws: WSClient, system: ActorSystem, cc: ControllerComponents , lifecycle: ApplicationLifecycle, pkg: PkGlobal) extends AbstractController(cc)   {
//object Application extends Controller   {

  pkg.onStartapp()
  val myCfg: Config =  ConfigFactory.parseFile(new File("conf/googlemaps.conf"))
  val path = "google.maps.apikey"
  val valorgma = if (myCfg.hasPath(path)) {myCfg.getString(path)} else {""}

  import scala.concurrent.duration._
  import akka.pattern.ask
  implicit val timeout: Timeout = 10.seconds
  
  //GOOD val promotionEngine = system.actorOf(PromotionEngine.props, "promoengine-actor")
  //val promotionEngine = system.actorOf(PromotionEngine.props(ws), "promoengine-actor")
  val promotionEngine: ActorRef = system.actorOf(BalancingPool(10).props(PromotionEngine.props(ws)), "promoengine-actor")
  
  
  lifecycle.addStopHook { () =>
    Future.successful(pkg.onStop())
  }
  
 def peServiceGet: Action[AnyContent] = Action.async {
	  ( promotionEngine ? GetPromotions("COD1")).mapTo[String].map 
			{ message => Ok(message) }
  }
  
  def peServicePP: Action[AnyContent] = Action.async {
	  ( promotionEngine ? PostPromotions("PP_Get")).mapTo[String].map 
			{ message => Ok(message) }
  }
  
  def peServiceFF: Action[AnyContent] = Action.async {
	  ( promotionEngine ? PostPromotionsAggregator("NoES")).mapTo[String].map 
			{ message => Ok(message) }
  }
  
    def peServiceFF_ES: Action[AnyContent] = Action.async {
	  ( promotionEngine ? PostPromotionsAggregator("/ticket/_search")).mapTo[String].map 
			{ message => Ok(message) }
  }
  
  def index = Action {
    Ok(views.html.index())
  }
  
  def maps = Action {
    if ( valorgma == "" )
      Ok("Necesario establecer el valor de \'google.maps.apikey\' en \'conf/googlemaps.conf\'")
      else
      Ok(views.html.maps(valorgma))
  }
  
  /**
   * Uses server timestamp to create a tick obj then produces it to kafka
   * @return
   */
  def putGenTick (tipo: String): Action[AnyContent] = Action.async { _ =>
    // Send it to Kafka
	//Logger.info("Enviado tick - " + tipo + " - tick connected")
	var msg_tipo: String = ""
	if( tipo == "1" ){
         msg_tipo = "Apertura"
      } else if( tipo == "2" ){
         msg_tipo = "Ticket"
  } else{
         msg_tipo = "Cierre"
      }
    TickProducer.produce(Tick(System.currentTimeMillis(),msg_tipo)).map { r =>
      if(r) {
        Ok(ViewModels.MSG_SUCCESS_JSON)
      }else{
        InternalServerError(ViewModels.MSG_ERROR_JSON)
      }
    }
  }

  def getLastOffset: Action[AnyContent] = Action.async { _ =>
    Future {
      TickSimpleConsumer.getLastOffset(TickProducer.TOPIC, TickSimpleConsumer.PARTITION_DEF) match {
        case None => InternalServerError(ViewModels.MSG_ERROR_JSON)
        case Some(offset) => Ok(Json.prettyPrint(Json.toJson[Msg](Msg("Last Offset: " + offset))))
      }
    }
  }

  def getLastMessages(count: Int): Action[AnyContent] = Action.async { _ =>
    Future {
      val r: Map[Long,(String, String)] =
        TickSimpleConsumer.fetchLastMessages(TickProducer.TOPIC, TickSimpleConsumer.PARTITION_DEF, count)
      if (r.isEmpty) {
        Ok(Json.prettyPrint(Json.toJson[Msg](Msg("Empty Results"))))
      } else {
        Ok(Json.prettyPrint(Json.toJson[Msg](Msg("Last Offset: " + r.mkString(" ")))))
      }
    }
  }

  def getResetOffset(back: Int): Action[AnyContent] = Action.async { _ =>
    Future {
      TickSimpleConsumer.getLastOffset(TickProducer.TOPIC, TickSimpleConsumer.PARTITION_DEF) match {
        case None => InternalServerError(ViewModels.MSG_ERROR_JSON)
        case Some(offset) => {
          val resetTo: Long = calcResetBack(offset, back)
          TickSimpleConsumer.resetOffset(TickProducer.TOPIC, TickSimpleConsumer.PARTITION_DEF, resetTo) match {
            case None => InternalServerError(ViewModels.MSG_ERROR_JSON)
            case Some(newOffset) => {
              if (Logger.isDebugEnabled) Logger.debug("getResetOffset last offset " + offset + " newOffset " + newOffset)
              Ok(Json.prettyPrint(Json.toJson[Msg](Msg("Last Offset: " + offset+ " newOffset " + newOffset))))
            }
          }
        }
      }
    }
  }

  def calcResetBack(offset: Long, back: Int): Long = {
    val nOffset = offset - back
    if (nOffset < 0) {
      0L
    } else {
      nOffset
    }
  }

  // Tick Feed - The Tick consumer will put to the tick chanel json pulled from kafka

  /** Enumeratee for detecting disconnect of the stream */
   //def logDisconnect(addr: String): Enumeratee[JsValue, JsValue] = {  //ORIGINAL
  def logDisconnect(addr: String): Enumeratee[String, String] = {
	  
    Enumeratee.onIterateeDone { () =>
      Logger.info(addr + " - tickOut disconnected")
    }
  }

  /** Controller action serving activity for tick json consumed from kafka */
  def tickFeed = Action { req =>
    Logger.info("FEED tick - " + req.remoteAddress + " - tick connected")
	
	// Implica la conexión con Kafka
	pkg.onStartDemo()
	
	val stringSource = Source.fromPublisher(IterateeStreams.enumeratorToPublisher(TickConsumer.tickOut &> Concurrent.buffer(100) &> logDisconnect(req.remoteAddress)))
	Ok.chunked(stringSource via EventSource.flow).as("text/event-stream")
	
  }

}