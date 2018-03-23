package com.vector.actors

import akka.actor._
import akka.util.Timeout
//import play.api.Logger
//import javax.inject._
import scala.concurrent.Await
import akka.routing.BalancingPool

import play.api.libs.ws._

object PromotionEngine {
case class GetPromotions(promotionID: String)
case class PostPromotions(promotionID: String)
case class PostPromotionsAggregator(promotionID: String)
case class SendResult(query: Int, result: String, reply: ActorRef)
case class SendResultAggregator1(result: String)
//GOOD def props: Props = Props[PromotionEngine]
def props(ws: WSClient): Props = Props(new PromotionEngine(ws))
}

//GOOD class PromotionEngine extends Actor {
class PromotionEngine(ws: WSClient) extends Actor {
	import PromotionEngine._
	import QueryES._
	//import context._
	import scala.concurrent.duration._
	import akka.pattern.ask
	implicit val timeout: Timeout = 10.seconds
	val replyTo: ActorRef = sender

	//val queryES = context.actorOf(QueryES.props(ws), "promoquery-actor")
	val queryES = context.actorOf(BalancingPool(10).props(QueryES.props(ws)), "promoquery-actor")
	def receive = {
    case GetPromotions(promotionID: String) => {
			val replyTo = sender
			
			//Alternativa 2
			val fut1 = (queryES ? GetQuery(promotionID)).mapTo[String]
			val result1 = Await.result(fut1, 10 seconds)
			val mensaje1 = "R1: " + result1
			val fut2 = (queryES ? GetQuery(promotionID)).mapTo[String]
			val result2 = Await.result(fut1, 10 seconds)
			val mensaje = mensaje1 + " R2: " + result2
			replyTo ! "GQ: " + mensaje
		}
	case PostPromotions(promotionID: String) => {
			queryES ! PostQuery(1,promotionID,sender)
		}
	case PostPromotionsAggregator(promotionID: String) => {
			// Direccionamiento de respuestas
			val replyTofinal = context.sender // responde directamente al iniciador primero (self.sender)
			//val replyTofinal = self		   // responde a este mismo servicio
			for (replyTof <- List(replyTofinal)) {
				val aggregator = context.actorOf(Props(new Aggregator(replyTof)))
				queryES ! PostQueryAggregator(1,promotionID, aggregator)
				queryES ! PostQueryAggregator(2,promotionID, aggregator)
			}
		}	
	case SendResult(query: Int,result: String, reply: ActorRef) => {
			reply ! "PQ"+ query.toString +": " + result
		}
	case SendResultAggregator1(result: String) => {
			//Responde al Engine Service
			replyTo !("PQA: " + result)
		}
  }
}