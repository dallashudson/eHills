package com.example;

import java.util.Queue;
import java.io.IOException;
import java.io.File;
import java.net.URL;
import java.nio.channels.*;
import java.nio.channels.ReadableByteChannel;
import java.io.FileOutputStream;
import java.nio.file.StandardCopyOption;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.ServerSocket;
import java.math.BigDecimal;
import java.util.TimerTask;
import java.net.Socket;
import java.util.LinkedList;
import java.util.ArrayList;
import java.math.RoundingMode;
import java.util.Timer;

import java.util.Observable;

public class Server extends Observable {

	private ArrayList<ClientHandler> clientList = new ArrayList<ClientHandler>();
	private Object availableLock = new Object();
	private ArrayList<AuctionItem> availableAuctions = new ArrayList<AuctionItem>();
	private Integer clientNum = 0;
	private Queue<AuctionItem> unavailableAuctionQueue = new LinkedList<AuctionItem>();
	final static BigDecimal sec = BigDecimal.valueOf(1.0).divide(BigDecimal.valueOf(60.0), 100, RoundingMode.HALF_UP);
	final static BigDecimal decimalZero = BigDecimal.ZERO;
	private ArrayList<AuctionItem> auctions = new ArrayList<AuctionItem>();

	private static final String database = "database.db";
	private static String AuctionHome = System.getProperty("user.home") + File.separator + ".auction_home";
	public static String DBHome = AuctionHome + File.separator + database;

	public static void main(String[] args) {
		File auctionHome = new File(AuctionHome);
		auctionHome.mkdir(); // Don't care about failure
		File db_home = new File(DBHome);
		try {
			InputStream in = Server.class.getClassLoader().getResourceAsStream(database);
			Files.copy(in, db_home.toPath(), StandardCopyOption.REPLACE_EXISTING); // Replace the DB everytime
		}catch(Exception exception) {
			exception.printStackTrace();
		}
		new Server().runServer();
	}

	private void runServer() {
		try {
			DatabaseConnection parser = new DatabaseConnection();
			parser.connect();
			auctions.addAll(parser.collectAuctionItems());
			availableAuctions.addAll(auctions);

			availTimer();
			unavailTimer();
			networkInitialization();

		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	private void networkInitialization() throws Exception {
		@SuppressWarnings("resource")
		ServerSocket serverSocket = new ServerSocket(4242);
		while (true) {
			Socket cliSock = serverSocket.accept();
			System.out.println("Client #" + clientNum + " will connect at " + cliSock);
			ClientHandler handler = new ClientHandler(this, cliSock, clientNum);

			this.addObserver(handler);

			clientNum = clientNum + 1;
			clientList.add(handler);

			Thread clientRunner = new Thread(handler);
			clientRunner.start();
		}
	}

	protected synchronized void processRequest(String inputCommand) {
		String output = "";
		if (inputCommand.contains("|")) {
			String[] commandString = inputCommand.trim().split("\\|");
			switch (commandString[0]) {
				case "changeItemBid":
					for (AuctionItem i : availableAuctions) {
						if (i.title.contentEquals(commandString[1])) {
							updateBid(commandString, i);
							break;
						}
					}
					break;
				default:
			}
		} else {

			String[] commandString = inputCommand.trim().split(" ");

			switch (commandString[0]) {
				case "disconnectClient":
					disconnectClientFromServer(commandString);
					break;
				case "setUpAuctionItems":
					setupItems(output, commandString);
					break;
				default:
			}
		}

	}

	private void setupItems(String output, String[] commandString) {
		ClientHandler handler = null;
		int clientID = Integer.parseInt(commandString[1]);

		for (ClientHandler cli : clientList) {
			if (cli.clientID == clientID) {
				handler = cli;
			}
		}

		String auctionTitle = "";

		for (AuctionItem i : auctions) {
			auctionTitle += i.title + "|" + i.desc + "|" + String.valueOf(i.reserve) + "|" +
					String.valueOf(i.bidCurrent) + "|" + String.valueOf(i.buyItNow) + "|" +
					i.highestBidder + "|" + String.valueOf(i.timer) + "|" + i.bidMessage + "|";
		}

		output += "setUpAuctionItemsNotification|" + auctionTitle;
		handler.sendToClient(output);
	}

	private void disconnectClientFromServer(String[] commandString) {
		ClientHandler disc = null;
		int num = Integer.parseInt(commandString[1]);
		for (ClientHandler o : clientList) {

			if (o.clientID == num) {
				disc = o;
				break;
			}
		}
		try {
			disc.apiReturn.flush();
			disc.apiReturn.close();
			disc.apiCall.close();
			disc.cliSock.close();

		} catch (IOException ex) {
			System.out.println("ERROR disconnecting Client from the Server.");
		}
		this.deleteObserver(disc);
		clientNum = clientNum - 1;
		clientList.remove(disc);
	}

	private void updateBid(String[] commandString, AuctionItem i) {

		i.highestBidder = i.highestBidder + commandString[3];
		i.bidCurrent = Double.parseDouble(commandString[2]);
		this.setChanged();
		this.notifyObservers(
				"changeItemBidNotification|" + i.title + "|" + i.bidCurrent + "|" + i.highestBidder);
	}

	private void availTimer() {
		Timer countdown = new Timer();
		Server serv = this;
		countdown.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				synchronized (availableLock) {
					for (AuctionItem x : availableAuctions) {
						runItemAuction(serv, x);
					}
				}
			}

			private void runItemAuction(Server serv, AuctionItem item) {

				if (item.timer.compareTo(sec) != 1) {
					item.timer = decimalZero;
					unavailableAuctionQueue.add(item);
					serv.setChanged();
					serv.notifyObservers("changedTimeNotification|" + item.title + "|" + item.timer);
				} else {
					item.timer = item.timer.subtract(sec);
					serv.setChanged();
					serv.notifyObservers("changedTimeNotification|" + item.title + "|" + item.timer);

				}
			}
		}, 0, 1000);
	}

	private void unavailTimer() {

		Timer countdown = new Timer();
		Server serv = this;
		countdown.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				synchronized (availableLock) {
					for (AuctionItem i : availableAuctions) {
						unavailableAuction(serv, i);
					}
					while (!unavailableAuctionQueue.isEmpty()) {
						availableAuctions.remove(unavailableAuctionQueue.remove());
					}
				}
			}

			private void unavailableAuction(Server obj, AuctionItem i) {
				if (i.timer.compareTo(decimalZero) == 0) {
					if (!(i.highestBidder.contentEquals("none"))) {
						i.bidMessage = (i.highestBidder + " purchased the item - " + i.title
								+ " - for the highest bidded price of $" + i.bidCurrent + ".");

					} else {
						i.bidMessage = "Auction for this item has ended. The item has been marked unsold.";
					}
					obj.setChanged();

					obj.notifyObservers("purchasedItemNotification|" + i.title + "|" + i.bidMessage);

				} else if (i.bidCurrent >= i.buyItNow) {

					i.bidMessage = (i.title + " was sold to " + i.highestBidder + " for the price of $"
							+ i.bidCurrent + ".");
					obj.setChanged();

					obj.notifyObservers("purchasedItemNotification|" + i.title + "|" + i.bidMessage);

					i.timer = decimalZero;
					unavailableAuctionQueue.add(i);
					obj.setChanged();

					obj.notifyObservers("changedTimeNotification|" + i.title + "|" + i.timer);
				}
			}
		}, 0, 50);
	}

}
