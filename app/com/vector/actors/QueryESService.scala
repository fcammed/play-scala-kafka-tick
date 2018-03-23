package com.vector.actors

import akka.actor._
import akka.pattern.pipe

import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.{Failure, Random, Success}

//WS
import play.api.libs.ws._
//WS

object QueryES {
case class GetQuery(queryString: String)
case class PostQuery(query: Int, queryString: String, reply: ActorRef)
case class PostQueryAggregator(query: Int, queryString: String, aggregator: ActorRef)
case class CallAPIesResult(query: Int, result: String, aggregator: ActorRef)
//GOOD def props: Props = Props[QueryES]
def props(ws: WSClient): Props = Props(new QueryES(ws))

def callAPIes(ws: WSClient, query: Int, queryString: String, aggregator: ActorRef) (implicit ec: ExecutionContext): Future[CallAPIesResult]  = {
	ws.url("http://10.202.10.120:9200" + queryString).get().map {
	// ws.url("http://localhost:3000").get().map {
		response => 
		CallAPIesResult(query, "QueryPQ: " + queryString + "-> Result: " + (response.json \ "hits" \ "total" ).get, aggregator)
		// CallAPIesResult(query, "QueryPQ: " + queryString + "-> Result: " + response, aggregator)
		}
	}
}

class QueryES (ws: WSClient) extends Actor with ActorLogging {
	import PromotionEngine._
	import QueryES._
	import context._
	
	val blockingEC: ExecutionContext = context.system.dispatchers.lookup("blocking-pool")
	
	val lista = List("palabra a", "palabra b", "palabra c", "palabra d")
	def receive = {
		case GetQuery(queryString: String) => {
			val replyTo = sender
			val f = Future{ blocking{ Thread.sleep(1)}}(blockingEC)
			val posts = lista(Random.nextInt(3))
			f onComplete {
				case Success(_) =>
					replyTo ! "QueryGQ: " + queryString + "-> Result: " + posts + " "
				case Failure(t) => replyTo ! "Error: " + t.getMessage
				}			
		}
		case PostQuery(query: Int, queryString: String, reply: ActorRef) => {
			val replyTo = sender
			val f = Future{ blocking{ Thread.sleep(1) } }(blockingEC)
			val posts = lista(Random.nextInt(3))
			f onComplete {
				case Success(_) =>
					replyTo ! SendResult(query, "QueryPQ: " + queryString + "-> Result: " + posts,reply)
				case Failure(t) => replyTo ! "Error: " + t.getMessage
				}
		}
		case CallAPIesResult (query: Int, result: String, aggregator: ActorRef) => {
			aggregator ! SendResult(query, result, aggregator)
		}
		case PostQueryAggregator(query: Int, queryString: String, aggregator: ActorRef) => {
			if (queryString == "NoES") {
				val f = Future{ blocking{ Thread.sleep(1)}}(blockingEC)
				val posts = lista(Random.nextInt(3))
				f onComplete {
					case Success(_) =>
						aggregator ! SendResult(query, "QueryPQ: " + queryString + "-> Result: " + posts, aggregator)
					case Failure(t) => aggregator ! SendResult(query, "Error: " + t.getMessage, aggregator)
					}
			} else {
				callAPIes(ws, query, queryString, aggregator) pipeTo self
				// val futureResult = ws.url("http://10.202.10.120:9200" + queryString).get().map {
					// response =>	
						// aggregator ! SendResult(query, "QueryPQ: " + queryString + "-> Result: " + (response.json \ "hits" \ "total" ).get, aggregator)
					// }
			}
		}
	}
}