package com.processor44.tick

import akka.actor.{Props, ActorLogging, Actor}
import com.typesafe.config.ConfigFactory
import java.util.Properties
import kafka.consumer._
import kafka.consumer.ConsumerIterator
import kafka.consumer.ConsumerConnector

import scala.collection.Map

/*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
*/
import kafka.message.MessageAndMetadata
import play.api.Logger
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.{Json, JsValue}
import java.net.URLDecoder


/**
 * Consumes messages from kafka topic defined by config "producer.topic.name.tick"
 */
object TickConsumer {

  lazy val CONF = ConfigFactory.load
  lazy val ZOOKEEPER_CON = CONF.getString("zookeeper.connect")
  lazy val GROUP_ID = "1"
  lazy val CONSUMER_CONFIG = new ConsumerConfig(buildConsumerProps)

  def buildConsumerProps: Properties = {
    val p = new Properties()
    p.put("group.id", GROUP_ID)
    p.put("zookeeper.connect", ZOOKEEPER_CON)
    p.put("auto.commit.enable", "true")  // If true, periodically commit to ZooKeeper the offset of messages already
    // fetched by the consumer. This committed offset will be used when the process fails as the position from which the
    // new consumer will begin.  Default 60 seconds.
    p
  }

  // Consumer actor
  case object Consume
  case object Shutdown
  case object Test
  //val propsTickConsumerActor = Props[TickConsumerActor]
  def propsTickConsumerActor(topic: String): Props = Props(new TickConsumerActor(topic))

  // To broadcast what is consumed from kafka out to web clients
  // tickOut is Enumerator[JsValue], tickChannel is Concurrent.Channel[JsValue]
  //val (tickOut, tickChannel) = Concurrent.broadcast[JsValue] //ORIGINAL
  val (tickOut, tickChannel) = Concurrent.broadcast[String]
  
  /** Conversion function for key bytes to String with null check */
  def getKeyAsString(key: Array[Byte], charsetName: String = TickProducer.CHARSET): String = {
    if (key == null) ""
    else (new String(key, charsetName))
  }

}

/**
 * Consumes from kafka topic defined by config "producer.topic.name.tick"
 * Feeds out to another stream
 */
class TickConsumerActor (topic: String) extends Actor with ActorLogging {

  val connector: ConsumerConnector = Consumer.create(TickConsumer.CONSUMER_CONFIG)

  /** */
  def receive = {
    case TickConsumer.Test => if (true) log.info("TickConsumer.Test!")
    case TickConsumer.Consume => {
      // if (log.isInfoEnabled) log.info("TickConsumerActor consuming...")
	  // Logger.info("TickConsumerActor consuming...0")
      val topicStreamMap: Map[String, List[KafkaStream[Array[Byte],Array[Byte]]]] = if( topic=="tick" ) connector.createMessageStreams(Map(TickProducer.TOPIC -> 1)) else connector.createMessageStreams(Map(TickProducer.TOPIC_event -> 1))
      val topic_name: String = if( topic=="tick" ) TickProducer.TOPIC else TickProducer.TOPIC_event

      topicStreamMap.get(topic_name) match {
        case None => {log.error("TickConsumerActor NONE for Stream.  Can't Consume.")
					Logger.info("TickConsumerActor No puede consumir ....0")}
        case Some(streamList) => {
          val kStream: KafkaStream[Array[Byte], Array[Byte]] = streamList(0)
          iterateStream(kStream)
        }
      }
    }
    case TickConsumer.Shutdown => {
      if (log.isInfoEnabled) log.info("TickConsumerActor shutting down...")
	  if (Logger.isDebugEnabled) Logger.debug("TickConsumerActor shutting down..." ) //BORRAR
      connector.shutdown
    }
  }

  /**
   * while (iter.hasNext()) ... consumeAndPublishOne for each
   * @param kStream
   */
  def iterateStream(kStream: KafkaStream[Array[Byte], Array[Byte]]): Unit = {
    val iter: ConsumerIterator[Array[Byte], Array[Byte]] = kStream.iterator()
    while (iter.hasNext()) {
      consumeAndPublishOne(iter.next())
    }
  }

  /**
   *
   * @param mam
   * @return
   */
  def consumeAndPublishOne(mam: MessageAndMetadata[Array[Byte], Array[Byte]]): Boolean = {
    try {
      val k = TickConsumer.getKeyAsString(mam.key, TickProducer.CHARSET)
      val m = new String(mam.message, TickProducer.CHARSET) // back to string json
	  // Logger.info("Recibido Flink: " +m)
	  // Logger.info("consumed [" + k + " " +  m + "] at partition " + mam.partition+ ", at offset " + mam.offset)
      // if (log.isDebugEnabled) log.debug("consumed [" + k + " " +  m + "] at partition " +
        // mam.partition+ ", at offset " + mam.offset)
	  val text_json = URLDecoder.decode(m, "UTF-8")
	  // Logger.info("Enviado Navegador: " + text_json )
	  TickConsumer.tickChannel.push(text_json) // broadcast it as String
      true
    } catch {
      case t: Throwable => {
        Logger.error("consumeAndPublishOne ERROR ", t)
        false
      }
    }
  }

}
