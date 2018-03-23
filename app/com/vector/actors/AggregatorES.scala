package com.vector.actors

import akka.actor._
import akka.util.Timeout

class Aggregator(replyTo: ActorRef) extends Actor {
    //self.dispatcher = backendDispatcher
    //self.lifeCycle = Supervision.Temporary
    //self.receiveTimeout = Some(1000)
	import QueryES._
	import PromotionEngine._
	import scala.concurrent.duration._
	import context._
	implicit val timeout: Timeout = 5.seconds

	var textResult1: Option[String] = None
	var textResult2: Option[String] = None

    def receive = {
		case SendResult(query: Int,result: String, reply: ActorRef) => {
			if(query == 1 ) {
				textResult1 = Some(result)
			} else if(query == 2 ){
				textResult2 = Some(result)
			} 
			replyWhenDone()
		}
      case ReceiveTimeout =>
        context.stop(self)
    }
	
    def replyWhenDone() {
      for (text1 <- textResult1; text2 <- textResult2) {
        // Direccionamiento de respuestas
		
		// Se direcciona la lista de respuestas al servicio que lanza las Queries (Motor)
		// Tiene sentido cuando el motor tenga que aplicar lógica sobre la lista de respuestas
		//replyTo ! SendResultAggregator1("R1: " + textResult1 + "R2: " + textResult2)
		
		// Direcciona la lista de respuestas directamente al servicio original (el que llama al Motor).
		// Sólo tiene sentido cuando no se aplique lógica sobre la lista de respuestas
		replyTo ! "R1: " + textResult1 + "R2: " + textResult2
        context.stop(self)
      }
    }
  }