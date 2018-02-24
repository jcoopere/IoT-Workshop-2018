package com.cloudera.accelerators.mqtt.mqtt_kafka_bridge;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

/**
 * A simple MQTT-to-Kafka bridge application.
 * 
 * Incoming message from a single MQTT topic are forwarded to a single Kafka topic using default settings.
 * 
 * A public MQTT broker is available at "tcp://iot.eclipse.org:1883".
 * 
 * @author jcooperellis
 *
 */
public class SimpleMqttKafkaBridge implements MqttCallback {
	@Parameter(names = { "--mqtt-broker" })
	private String mqttBroker;
	@Parameter(names = { "--mqtt-client-id" })
	private String mqttClientId;
	@Parameter(names = { "--mqtt-topic" })
	private String mqttTopic;
	@Parameter(names = { "--kafka-topic" })
	private String kafkaTopic;
	@Parameter(names = { "--kafka-broker-list" })
	private String kafkaBrokerList;

	private MqttClient client;
	private KafkaProducer<String, String> producer;

	public static void main(String[] args) {
		if (args.length != 10) {
			System.out.println("Usage: SimpleMqttKafkaBridge --mqtt-broker <mqtt broker> --mqtt-client-id <mqtt client id> --mqtt-topic <mqtt topic to listen on> --kafka-topic <kafka topic to publish to> --kafka-broker-list <csv list of kafka brokers>");
			System.exit(1);
		}
		SimpleMqttKafkaBridge main = new SimpleMqttKafkaBridge();
		new JCommander(main, args);
		main.startBridge();
	}

	/**
	 * Start the bridge, and keep it running until the program exits.
	 */
	private void startBridge() {
		try {
			if (mqttBroker == null || mqttClientId == null || mqttTopic == null || kafkaTopic == null || kafkaBrokerList == null) {
				System.out.println("Usage: SimpleMqttKafkaBridge --mqtt-broker <mqtt broker> --mqtt-client-id <mqtt client id> --mqtt-topic <mqtt topic to listen on> --kafka-topic <kafka topic to publish to> --kafka-broker-list <csv list of kafka brokers>");
				System.exit(1);
			}
			
			System.out.println("MQTT Broker: " + mqttBroker);
			System.out.println("MQTT Client ID: " + mqttClientId);
			System.out.println("MQTT Topic: " + mqttTopic);
			System.out.println("Kafka Topic: " + kafkaTopic);
			System.out.println("Kafka Broker List: " + kafkaBrokerList);
			
			// Initialize Kafka producer.
			Properties kafkaProps = new Properties();
			kafkaProps.put("bootstrap.servers", kafkaBrokerList);
			kafkaProps.put("metadata.broker.list", kafkaBrokerList);
			kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			kafkaProps.put("acks", "0");
			kafkaProps.put("retries", "3");
			kafkaProps.put("producer.type", "async");
			kafkaProps.put("linger.ms", "200");
			kafkaProps.put("batch.size", "1000");
			
			producer = new KafkaProducer<>(kafkaProps);
			
			this.connectMqtt();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Initialize MQTT client.
	 * 
	 * @return MQTT client.
	 */
	private void connectMqtt() {
		try {
			if (!mqttBroker.startsWith("tcp://")) {
				mqttBroker = "tcp://" + mqttBroker;
			}


MqttConnectOptions options = new MqttConnectOptions();
options.setUserName("kapua-sys");
options.setPassword("kapua-password".toCharArray());

			client = new MqttClient(mqttBroker, mqttClientId);
			client.connect(options);
			client.setCallback(this);
			client.subscribe(mqttTopic);
			System.out.println("INFO: Connected to " + mqttBroker + " as client ID: " + mqttClientId + " Listening on topic: " + mqttTopic);
		} catch (MqttException e) {
			System.out.println("ERROR: Connection Failed!");
			e.printStackTrace();
			System.out.println("INFO: Retrying connection...");
			this.connectMqtt();
		}
	}

	/**
	 * MQTT connection lost callback
	 */
	public void connectionLost(Throwable e) {
		System.out.println("ERROR: Connection lost!");
		e.printStackTrace();
		System.out.println("INFO: Reconnecting...");
		this.connectMqtt();
	}

	/**
	 * MQTT delivery complete callback
	 */
	public void deliveryComplete(IMqttDeliveryToken token) {
		System.out.println("Delivery complete!");
		System.out.println("Delivery Token: " + token.toString());
	}

	/**
	 * MQTT message arrived callback. Forwards message to Kafka on the
	 * appropriate topic.
	 */
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		ProducerRecord<String, String> kafkaMessage = new ProducerRecord<String, String>(kafkaTopic, message.toString());
		
		System.out.println("Bridged message: " + message.toString());

		producer.send(kafkaMessage);
	}

}
