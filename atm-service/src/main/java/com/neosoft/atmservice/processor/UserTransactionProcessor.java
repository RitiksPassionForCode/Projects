package com.neosoft.atmservice.processor;

import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Aggregator;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import com.neosoft.atmservice.dto.Account;
import com.neosoft.atmservice.dto.Transaction;
import com.neosoft.atmservice.repository.UserRepository;

@Component
public class UserTransactionProcessor {
	
	@Autowired
	UserRepository repository;

	public void streamTopology() {
		
		Properties properties = new Properties();
		properties.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, "account-transaction-producer");
		properties.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
		properties.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
		properties.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
		properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		// Create a StreamsBuilder to define the processing topology
		StreamsBuilder builder = new StreamsBuilder();

//		// Create a KStream that produces messages from the transaction topic
//		KStream<String, Account> transactionStream = builder.stream("account-transactions");
//		
//        // Process the transactions
//        transactionStream.groupByKey()
//        .aggregate( () -> new Aggregator<Transaction, Account>() {
//        	        public Account update(Transaction transaction, Account account) {
//        	            // Update the account balance based on the transaction
//        	        	updateAccount(transaction, account);
//        	            return account;
//        	        }
//        	    },
//        	    Materialized.<String, Account, KeyValueStore<Bytes, byte[]>>as("account-store")
//        	        .withKeySerde(Serdes.String())
//        	        .withValueSerde(new JsonSerde<>(Account.class)))
//        			.toStream()
//					.to("account-balance-updates", Produced.with(Serdes.String(), new JsonSerde<>(Account.class)));
		
		  KStream<String, Transaction> transactions = builder.stream("account-transactions",
	                Consumed.with(Serdes.String(), Serdes.serdeFrom(Transaction.class)));

	        transactions.foreach((accountNumber, transaction) -> {
	            Account account = getAccount(accountNumber);
	            if (account != null) {
	                if (transaction.getTransactionType().equals("deposit")) {
	                    account.deposit(transaction.getAmount());
	                } else if (transaction.getTransactionType().equals("withdraw")) {
	                    try {
	                        account.withdraw(transaction.getAmount());
	                    } catch (IllegalArgumentException e) {
	                        // Handle insufficient funds error
	                        System.out.println(e.getMessage());
	                    }
	                }
	                updateAccount(transaction, account);
	            }
	        });

		// Build and start the Kafka Streams application
		KafkaStreams kafkaStreams = new KafkaStreams(builder.build(), properties);
		kafkaStreams.start();

		// Add shutdown hook to gracefully close the Kafka Streams application
		Runtime.getRuntime().addShutdownHook(new Thread(kafkaStreams::close));
	}

//	// Helper method to extract the account number from the transaction message
//	private static String extractAccountNumber(String transaction) {
//		 // Parse the transaction JSON and extract the account number
//	    JSONObject jsonTransaction = new JSONObject();
//	    String accountNumber = null;
//		try {
//			jsonTransaction = new JSONObject(transaction);
//			accountNumber = jsonTransaction.getString("accountNumber");
//		} catch (JSONException e) {
//			e.printStackTrace();
//		}
//	    return accountNumber;
//	}

	// Helper method to update the account balance based on the transaction
	private static Account updateAccount(Transaction transaction, Account account) {
		// Parse the transaction JSON and update the account balance accordingly
	    JSONObject jsonTransaction;
		try {
			jsonTransaction = new JSONObject(transaction);
			String transactionType = jsonTransaction.getString("transactionType");
		    double amount = jsonTransaction.getDouble("amount");
		    
		    if ("deposit".equals(transactionType)) {
		        account.deposit(amount);
		    } else if ("withdrawal".equals(transactionType)) {
		        account.withdraw(amount);
		    }
		} catch (JSONException e) {
			e.printStackTrace();
		}
	   
	    return account;
	}
	
//	// Helper method to publish the updated account balance to a Kafka topic
//	private static void publishAccountBalance(String accountNumber, Account account) {
//	    // Create Kafka producer properties
//	    Properties properties = new Properties();
//	    properties.setProperty("bootstrap.servers", "localhost:9092");
//	    properties.setProperty("key.serializer", StringSerializer.class.getName());
//	    properties.setProperty("value.serializer", JsonSerde.class.getName());
//
//	    // Create Kafka producer
//	    KafkaProducer<String, Account> producer = new KafkaProducer<>(properties);
//
//	    // Publish the updated account balance along with the account number to the topic
//	    ProducerRecord<String, Account> record = new ProducerRecord<>("account-balance-updates", accountNumber, account);
//	    producer.send(record);
//
//	    // Close the Kafka producer
//	    producer.close();
//	}
	
	private Account getAccount(String accountNumber) {
        // Implement the logic to retrieve the Account object based on the account number
		Account accountByAccountNo = repository.findByAccountNumber(accountNumber);
		if(accountByAccountNo == null) {
			return null;
		}
        return accountByAccountNo;
    }

}