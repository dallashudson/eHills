
package com.example;

import java.io.PrintWriter;
import java.util.logging.Logger;
import java.math.BigDecimal;
import java.net.Socket;
import java.text.DecimalFormat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Queue;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.Pair;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.media.Media;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.FontPosture;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;

public class Client extends Application {

	private static PrintWriter toServer;
	private static BufferedReader fromServer;
	private static Socket sock;

	private static final BigDecimal decimal_ten = BigDecimal.valueOf(0.175);
	private static final BigDecimal dec_zero = BigDecimal.ZERO;
	private static String localhost = "127.0.0.1";
	private static final DecimalFormat decimalForm = new DecimalFormat("#.00");

	private static boolean auctionsUpdated = false;
	private static boolean updatesComplete = false;
	private static boolean onWatch = false;

	private static Queue<String> purchasedItems = new LinkedList<String>();
	private static Queue<String> bidHistory = new LinkedList<String>();
	private static String user = null;
	private static ArrayList<AuctionItem> auctionItems = new ArrayList<AuctionItem>();
	private static ArrayList<AuctionItem> watchedItems = new ArrayList<AuctionItem>();
	private static ArrayList<Pair<String, VBox>> wlPairs = new ArrayList<Pair<String, VBox>>();
	private static Object wLock = new Object();
	private static ArrayList<Thread> threadList = new ArrayList<Thread>();
	private static ArrayList<String> auctionNames = new ArrayList<String>();
	private static HashSet<String> watchNames = new HashSet<String>();

	private static MediaPlayer logonSound = null;
	private static MediaPlayer exitSound = null;
	private static MediaPlayer errorSound = null;
	private static MediaPlayer mouseSound = null;
	private static MediaPlayer watchAddSound = null;
	private static MediaPlayer watchRemoveSound = null;
	private static MediaPlayer purchasedSound = null;
	private static MediaPlayer bidSound = null;

	private static Logger logger = java.util.logging.Logger.getLogger("ehills");

	/**
	 * initializeDataFields
	 * Initializes all data fields (boolean flags and data structure) of the Client
	 * class to their defaults when a client clicks logout button
	 */
	private static void initializeDataFields() {

		auctionsUpdated = false;
		updatesComplete = false;
		onWatch = false;

		purchasedItems.clear();
		bidHistory.clear();
		user = null;
		auctionItems = new ArrayList<AuctionItem>();
		watchedItems = new ArrayList<AuctionItem>();
		wlPairs = new ArrayList<Pair<String, VBox>>();
		wLock = new Object();
		threadList = new ArrayList<Thread>();
		auctionNames = new ArrayList<String>();
		watchNames = new HashSet<String>();
	}

	/**
	 * main
	 * Launches Java FX Application thread
	 */
	public static void main(String[] args) {
		launch(args);
	}

	/**
	 * start
	 * The main entry point for all JavaFX applications
	 */
	@Override
	public void start(Stage screen) {
		soundSetup();
		screen.setTitle("Logon to eHills");
		screen.setScene(loginScene(screen));
		screen.show();
	}

	private static void soundEffect(MediaPlayer media) {

		media.seek(Duration.ZERO);
		media.play();

	}

	public static void soundSetup() {

		Media logonWav = new Media(Client.class.getClassLoader().getResource("logon.wav").toString());
		logonSound = new MediaPlayer(logonWav);

		Media exitWav = new Media(Client.class.getClassLoader().getResource("exit.wav").toString());
		exitSound = new MediaPlayer(exitWav);

		Media errWav = new Media(Client.class.getClassLoader().getResource("err.wav").toString());
		errorSound = new MediaPlayer(errWav);

		Media mouseWav = new Media(Client.class.getClassLoader().getResource("mouse.wav").toString());
		mouseSound = new MediaPlayer(mouseWav);

		Media addWatchlistWav = new Media(
				Client.class.getClassLoader().getResource("addWatchlist.wav").toString());
		watchAddSound = new MediaPlayer(addWatchlistWav);

		Media remWatchlistWav = new Media(
				Client.class.getClassLoader().getResource("remWatchlist.wav").toString());
		watchRemoveSound = new MediaPlayer(remWatchlistWav);

		Media purchaseWav = new Media(Client.class.getClassLoader().getResource("purchase.wav").toString());
		purchasedSound = new MediaPlayer(purchaseWav);

		Media bidWav = new Media(Client.class.getClassLoader().getResource("bid.wav").toString());
		bidSound = new MediaPlayer(bidWav);
	}

	private static void sendToServer(String commandString) {
		// logger.log(null, "Sending to server: " + commandString);
		toServer.println(commandString);
		toServer.flush();
	}

	private static void disconnectClientFromServer() throws IOException {

		Client.sendToServer("disconnectClient");
	}

	private static String timeFormat(BigDecimal timeLeft) {
		String out = "";
		int totalTime = timeLeft.intValue();
		int hours = totalTime / 60;
		int minutes = totalTime % 60;
		BigDecimal decimalView = timeLeft.subtract(new BigDecimal(totalTime));
		int secs = (decimalView.multiply((new BigDecimal(60)))).intValue();

		String hourString = String.format("%02d", hours);
		String minuteString = String.format("%02d", minutes);
		String secondString = String.format("%02d", secs);

		out = "  Time Remaining: " + hourString + ":" + minuteString + ":" + secondString;
		return out;
	}

	private static void setUpNetworking() throws Exception {
		try {
			sock = new Socket(localhost, 4242);
		} catch (IOException ex) {

			// logger.log(null, "Error in setting up socket connection!");
		}
		// logger.log(null, "Connecting to the server through connection to " + sock);

		fromServer = new BufferedReader(new InputStreamReader(sock.getInputStream()));

		toServer = new PrintWriter(sock.getOutputStream());

		Thread serverCommandThread = new Thread(new Runnable() {
			@Override
			public void run() {
				String apiCall;
				try {
					while (!sock.isClosed() && !updatesComplete) {
						if (fromServer.ready()) {
							apiCall = fromServer.readLine();
							parseRequest(apiCall);
						}
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
		serverCommandThread.setName("serverCommandThread");
		serverCommandThread.start();
		threadList.add(serverCommandThread);
	}

	private static Scene loginScene(Stage screen) {

		BorderPane greetingBpane = new BorderPane();

		greetingBpane.setStyle("-fx-background-image: url(\"/milky-way-stars.png\");");

		Label greetingLabel = welcome();
		Label entryLabel = continueLbl();
		VBox greetingVBox = createGreeting(greetingLabel, entryLabel);
		Label warningLabel = createErrorlbl();

		TextField usernameInput = userNameField();
		TextField passwordInput = passwordField();

		Button exitButton = quitButton();
		welcomePlacement(greetingBpane, exitButton);

		exitButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {

				soundEffect(exitSound);

				Platform.exit();

				System.exit(0);
			}
		});

		Button loginButton = loginButton();
		HBox errorHBox = new HBox(15, loginButton, warningLabel);

		loginButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public synchronized void handle(ActionEvent event) {

				String passEntry = passwordInput.getText();

				String userEntry = usernameInput.getText();

				if (!(userEntry.equals("")) && !(passEntry.equals(""))) {
					try {

						login(screen, warningLabel, userEntry);
					} catch (Exception ex) {
						loginFailed(warningLabel, usernameInput, passwordInput, ex);
					}
				}

				else if (userEntry.equals("") && passEntry.equals("")) {
					noInput(warningLabel, usernameInput, passwordInput);
				}

				else if (userEntry.equals("")) {
					passNoUser(warningLabel, usernameInput, passwordInput);
				}

				else if (passEntry.equals("")) {
					userNoPass(warningLabel, usernameInput, passwordInput);
				}
			}

			private void userNoPass(Label errorLabel, TextField userTextField, TextField passTextField) {
				errorLabel.setText("Error - A password was not entered.");

				soundEffect(errorSound);

				userTextField.clear();
				passTextField.clear();
			}

			private void passNoUser(Label errorLabel, TextField userTextField, TextField passTextField) {
				errorLabel.setText("Error - A username was not entered.");
				soundEffect(errorSound);
				userTextField.clear();
				passTextField.clear();
			}

			private void noInput(Label errorLabel, TextField userTextField, TextField passTextField) {
				errorLabel.setText("Error - Enter a username and password.");
				soundEffect(errorSound);
				userTextField.clear();
				passTextField.clear();
			}

			private void loginFailed(Label errorLabel, TextField userTextField, TextField passTextField, Exception ex) {
				ex.printStackTrace();
				errorLabel.setText("An error occured while trying to connect to the server.");
				soundEffect(errorSound);
				userTextField.clear();
				passTextField.clear();
			}

			private void login(Stage primaryStage, Label errorLabel, String enteredUsername) throws Exception {
				errorLabel.setText("");
				setUpNetworking();
				sendToServer("setUpAuctionItems");
				while (!auctionsUpdated) {
					Thread.yield();
				}
				soundEffect(logonSound);
				user = enteredUsername;

				primaryStage.setTitle("eHills Auction");
				primaryStage.setScene(auctionSceneDisplay(primaryStage));
				primaryStage.show();
			}
		});

		Button guestButton = guestButton();

		guestButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public synchronized void handle(ActionEvent event) {
				try {
					guestLogin(screen, warningLabel);
				} catch (Exception ex) {
					guestError(warningLabel, usernameInput, passwordInput, ex);
				}
			}

			private void guestError(Label errorLabel, TextField userTextField, TextField passTextField, Exception ex) {
				ex.printStackTrace();
				errorLabel.setText("An error occured while trying to connect to the server.");
				soundEffect(errorSound);
				userTextField.clear();
				passTextField.clear();
			}

			private void guestLogin(Stage primaryStage, Label errorLabel) throws Exception {
				errorLabel.setText("");
				setUpNetworking();
				sendToServer("setUpAuctionItems");
				while (!auctionsUpdated) {
					Thread.yield();
				}
				soundEffect(logonSound);
				user = "Guest";

				primaryStage.setTitle("eHills Auction");
				primaryStage.setScene(auctionSceneDisplay(primaryStage));
				primaryStage.show();
			}
		});

		vertAlign(greetingBpane, greetingVBox, usernameInput, passwordInput, errorHBox, guestButton);

		return new Scene(greetingBpane, 500, 400);
	}

	private static void vertAlign(BorderPane greetingPane, VBox greetingVBox, TextField usernameField,
		TextField passwordText, HBox errorHBox, Button guestButton) {
		VBox mainVBox = new VBox(6, greetingVBox, usernameField, passwordText, errorHBox, guestButton);
		mainVBox.setMaxSize(410, 910);
		VBox.setMargin(greetingVBox, new Insets(0, 12, 17, 12));
		VBox.setMargin(usernameField, new Insets(0, 12, 0, 12));
		VBox.setMargin(passwordText, new Insets(0, 12, 0, 12));
		VBox.setMargin(errorHBox, new Insets(0, 12, 0, 12));
		VBox.setMargin(guestButton, new Insets(17, 12, 0, 12));
		greetingPane.setCenter(mainVBox);
	}

	private static Button guestButton() {
		Button guestButton = new Button("Continue as guest");
		guestButton.setStyle("-fx-text-fill: blue; -fx-border-color: blue; font-weight: bold;");
		guestButton.setFont(Font.font("Verdana", 11));
		guestButton.setAlignment(Pos.BASELINE_CENTER);
		return guestButton;
	}

	private static Button loginButton() {
		Button loginButton = new Button("Login");
		loginButton.setPrefSize(70, 20);
		loginButton.setStyle("-fx-text-fill: blue; -fx-border-color: blue; font-weight: bold;");
		loginButton.setFont(Font.font("Verdana", 15));
		loginButton.setAlignment(Pos.BASELINE_CENTER);
		return loginButton;
	}

	private static void welcomePlacement(BorderPane welcomePane, Button quitButton) {
		HBox rBox = new HBox(quitButton);
		rBox.setAlignment(Pos.BOTTOM_RIGHT);
		rBox.setPadding(new Insets(10));
		welcomePane.setBottom(rBox);
	}

	private static Button quitButton() {
		Button quitButton = new Button("Quit");
		quitButton.setPrefSize(80, 20);
		quitButton.setStyle("-fx-text-fill: red; -fx-border-color: blue; font-weight: bold;");
		quitButton.setFont(Font.font("Verdana", 20));
		return quitButton;
	}

	private static TextField passwordField() {
		TextField passwordEntry = new PasswordField();
		passwordEntry.setPromptText("Enter Password");
		passwordEntry.setFont(Font.font("Verdana", FontPosture.ITALIC, 11));
		passwordEntry.setStyle("-fx-text-fill: dimgray;");
		return passwordEntry;
	}

	private static TextField userNameField() {
		TextField usernameEntry = new TextField();
		usernameEntry.setPromptText("Enter Username");
		usernameEntry.setFont(Font.font("Verdana", FontPosture.ITALIC, 11));
		usernameEntry.setStyle("-fx-text-fill: dimgray;");
		return usernameEntry;
	}

	private static Label createErrorlbl() {
		Label warningLabel = new Label();
		warningLabel.setFont(Font.font("Verdana", FontPosture.ITALIC, 12));
		warningLabel.setTextFill(Color.RED);
		return warningLabel;
	}

	private static VBox createGreeting(Label greetingLabel, Label entryLabel) {
		VBox greetingVBox = new VBox(greetingLabel, entryLabel);
		greetingVBox.setAlignment(Pos.TOP_CENTER);
		VBox.setMargin(greetingLabel, new Insets(15, 0, 0, 0));
		return greetingVBox;
	}

	private static Label continueLbl() {
		Label entryLabel = new Label();
		entryLabel.setText("Please enter a username and password or continue as guest:");
		entryLabel.setFont(Font.font("Verdana", FontPosture.ITALIC, 12));
		entryLabel.setTextFill(Color.DIMGRAY);
		return entryLabel;
	}

	private static Label welcome() {
		Label greetingLabel = new Label();
		greetingLabel.setText("Welcome to eHills");
		greetingLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 30));
		greetingLabel.setTextFill(Color.BLUE);
		return greetingLabel;
	}

	private static void parseRequest(String serverCommand) {

		String[] command = serverCommand.split("\\|");

		switch (command[0]) {

			case "purchasedItemNotification":
				purchasedNotif(command);
				break;

			case "changeItemBidNotification":
				bidChangeAlert(command);
				break;

			case "changedTimeNotification":
				timeChangeAlert(command);
				break;

			case "setUpAuctionItemsNotification":
				auctionSetNotif(command);
				break;
		}
	}

	private static void auctionSetNotif(String[] command) {

		auctionItems.clear();

		String itemOut = "";
		Double buyItNow = 0.00;
		String title = "";
		BigDecimal timerAuc = null;
		String desc = "";
		Double reserve = 0.00;
		String currentHighName = "";
		Double currentBidAm = 0.00;

		for (int i = 1; i < command.length; i++) {
			if (!command[i].contentEquals("")) {
				if (i % 8 == 1) {
					title += command[i];
					auctionNames.add(title);
				} else if (i % 8 == 3) {
					reserve = Double.parseDouble(command[i]);
				} else if (i % 8 == 5) {
					buyItNow = Double.parseDouble(command[i]);
				} else if (i % 8 == 7) {
					timerAuc = new BigDecimal(command[i]);
				} else if (i % 8 == 2) {
					desc += command[i];
				} else if (i % 8 == 4) {
					currentBidAm = Double.parseDouble(command[i]);
				} else if (i % 8 == 6) {
					currentHighName = command[i];
				} else {
					itemOut = command[i];
					auctionItems.add(new AuctionItem(buyItNow, title, timerAuc, currentBidAm, desc,
							itemOut, reserve, currentHighName));
					title = "";
					currentHighName = "";
					desc = "";
				}
			}
		}
		auctionsUpdated = true;
	}

	private static void timeChangeAlert(String[] commandString) {
		String nameAuctionItem = commandString[1];
		BigDecimal updatedTimeLeft = new BigDecimal(commandString[2]);
		for (AuctionItem i : auctionItems) {
			if (i.title.contentEquals(nameAuctionItem)) {
				i.timer = updatedTimeLeft;
				break;
			}
		}
	}

	private static void bidChangeAlert(String[] commandString) {
		String updateItem = commandString[1];
		Double newBid = Double.parseDouble(commandString[2]);
		String newHighest = commandString[3];

		for (AuctionItem i : auctionItems) {
			if (i.title.contentEquals(updateItem)) {
				i.bidCurrent = newBid;
				i.highestBidder = newHighest;
				bidHistory.add(newHighest + " placed a bid of $"
						+ decimalForm.format(newBid) + " on item: " + i.title);
				break;
			}
		}
	}

	private static void purchasedNotif(String[] commandString) {
		String winner = commandString[1];
		String endedItem = commandString[2];
		for (AuctionItem i : auctionItems) {
			if (i.title.contentEquals(winner)) {
				i.bidMessage = endedItem;
				if (endedItem.contains("Auction for this item has ended. The item has been marked unsold.")) {
					purchasedItems.add(i.title.toUpperCase() + ": " + endedItem);
				} else {
					purchasedItems.add(endedItem);
				}
				break;
			}
		}
	}

	private static Scene auctionSceneDisplay(Stage screen) {

		Label greetingLabel = auctionGreeting();
		Button logoff = auctionLogout();
		Label itemsFeatured = itemsMenuLbl();
		ChoiceBox<String> itemSelections = itemsDropDown();

		itemSelections.setOnMouseClicked(e -> {
			soundEffect(mouseSound);
		});
		itemSelections.getStylesheets().add("choicebox.css");

		Button add = addButton();
		Button remove = removeButton();
		Label wlAddErr = watchlistError();
		Label bid = bidEntry();
		TextField bidEntry = bidInput();
		Button bidButton = placeBid();
		Label bidError = bidError();
		Label watchlist = watchlist();
		ListView<VBox> userWatchlist = watchingListView();

		Label notifications = new Label("Notifications:");
		TabPane changes = new TabPane();
		Tab purchases = new Tab("Purchases", new Label("Purchase History of the eHills Auction"));
		Tab bidsPlaced = new Tab("Bids Placed", new Label("Bidding History of the eHills Auction"));

		tabSetup(notifications, changes, purchases, bidsPlaced);

		ListView<String> purchase = new ListView<String>();
		ListView<String> bids = new ListView<String>();

		tabFill(changes, purchase, bids);

		HBox top = new HBox(300, greetingLabel, logoff);
		HBox middle = new HBox(6, itemsFeatured, itemSelections, add, remove, wlAddErr);
		HBox bottom = new HBox(6, bid, bidEntry, bidButton, bidError);
		VBox boxStack = new VBox(6, middle, bottom);

		HBsetup(greetingLabel, logoff, top, middle, bottom);

		Separator actions = new Separator();
		Separator watchStack = new Separator();
		VBox finalStack = new VBox(5, top, boxStack, actions, watchlist,
				userWatchlist, watchStack, notifications, changes);
		auctionDisplay(watchlist, userWatchlist, notifications, changes, finalStack);

		Thread notifications_thread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (!updatesComplete) {

					while (!bidHistory.isEmpty()) {

						String bidDisplay = bidHistory.remove();
						Platform.runLater(() -> {
							bids.getItems().add(bidDisplay.replace(user, "you"));
						});
					}

					while (!purchasedItems.isEmpty()) {

						sellAlert();
					}

					try {

						Thread.sleep(200);
					} catch (InterruptedException e) {
						// Logger alertLog;
						// alertLog.log(null, "Thread Updating Notifications INTERRUPTED.");
						// logger.log(null, "Thread Updating Notifications INTERRUPTED.");
					}
				}
			}

			private void sellAlert() {
				String selling = purchasedItems.remove();

				Platform.runLater(() -> {

					if (!(selling.contains(user))) {
						purchase.getItems().add(selling);

						soundEffect(purchasedSound);
					} else {
						purchase.getItems().add(selling.replace(user, "you"));

						soundEffect(purchasedSound);
					}
				});
			}
		});

		notifications_thread.setName("notifications");

		Thread buttons_thread = new Thread(new Runnable() {
			@Override
			public void run() {

				while (!updatesComplete) {

					if (itemSelections.getValue() != null) {
						itemIsSelected(add, bidEntry, bidButton);
					} else {
						noItemSelected(add, remove, bidEntry, bidButton);
					}

					if (watchNames.isEmpty() || !watchNames.contains(itemSelections.getValue())
							|| watchedItems.isEmpty()) {
						remove.setDisable(true);
					}

					else {
						remove.setDisable(false);
					}
				}
			}

			private void noItemSelected(Button addButt, Button remButt, TextField bidTextField, Button bidButt) {
				addButt.setDisable(true);
				bidButt.setDisable(true);
				remButt.setDisable(true);
				bidTextField.setDisable(true);
			}

			private void itemIsSelected(Button addButt, TextField bidTextField, Button bidButt) {
				addButt.setDisable(false);
				bidTextField.setDisable(false);

				if (bidTextField.getText().isEmpty()) {
					bidButt.setDisable(true);
				} else {

					bidButt.setDisable(false);
				}
			}
		});

		buttons_thread.setName("buttons");

		Thread watchlist_thread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (!updatesComplete) {
					synchronized (wLock) {

						for (Pair<String, VBox> wlNode : wlPairs) {

							String name = wlNode.getKey();

							VBox infoVBox = wlNode.getValue();

							VBox itemDetailsVBox = (VBox) infoVBox.lookup("#itemDetailsVBox");
							HBox biddingHBox = (HBox) itemDetailsVBox.getChildren().get(0);
							Label bdding = (Label) biddingHBox.lookup("#biddingLabel");
							Label timer = (Label) biddingHBox.lookup("#timeLeftLabel");
							Label selling = (Label) itemDetailsVBox.lookup("#sellLabel");

							for (AuctionItem i : watchedItems) {
								updateWatchedItem(name, i, infoVBox, timer, selling);
							}
						}
					}
					try {

						Thread.sleep(100);
					} catch (InterruptedException ex) {

						ex.printStackTrace();
					}
				}
			}

			private void updateWatchedItem(String nameKey, AuctionItem i, VBox infoVBox, Label timeLeftLabel, Label sellLabel) {
				if (nameKey.contentEquals(i.title)) {

					BigDecimal timeRemaining = i.timer;
					String timeLeftString = timeFormat(timeRemaining);

					Platform.runLater(() -> {
						String currBiddingString = "$" + decimalForm.format(i.bidCurrent);

						if (i.bidCurrent == 0.00) {
							currBiddingString = "none";
						}

						bid.setText(
								"Minimum Bid Price: $" + decimalForm.format(i.reserve) + "  Buy Now Price: $" +
										decimalForm.format(i.buyItNow) + "  Highest Bid: " + currBiddingString +
										"  Highest Bidder Username: " + i.highestBidder);

						if (timeRemaining.compareTo(decimal_ten) == -1) {
							timeLeftLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
							timeLeftLabel.setTextFill(Color.RED);
						}
						timeLeftLabel.setText(timeLeftString);

						if (!i.bidMessage.contentEquals("Item is available for purchase!")) {
							sellLabel.setText(i.bidMessage);
						}

						if (timeRemaining.compareTo(dec_zero) == 0) {
							infoVBox.setDisable(true);
						}
					});
				}
			}
		});

		watchlist_thread.setName("threadUpdatingWatch");

		startThreads(notifications_thread, buttons_thread, watchlist_thread);

		bidButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {

				String itemBidOn = itemSelections.getValue();

				Double bidSet = Double.parseDouble(bidEntry.getText());

				for (AuctionItem i : auctionItems) {
					if (i.title.contentEquals(itemBidOn)) {

						if (i.timer.doubleValue() <= 0.00) {
							itemExpired(bidError, itemBidOn);
						}

						else if (bidSet <= i.reserve) {
							reserveNotMet(bidError);
						}

						else if (bidSet <= i.bidCurrent) {
							lowBid(bidError);
						}

						else {
							validBid(wlAddErr, bidEntry, bidError, itemBidOn, bidSet, i);
						}
						break;
					}
				}
			}

			private void validBid(Label error, TextField bid, Label bidError,
					String itemBid, Double bidAmount, AuctionItem i) {

				clearFields(error, bidError);

				if ((bidAmount <= i.buyItNow)) {
					highestBidder(bid, bidError, itemBid, bidAmount);
				}

				else {
					winner(bid, bidError, itemBid, bidAmount);
				}
			}

			private void winner(TextField bidEntry, Label bidError, String bidItem,
					Double bidPrice) {

				sendToServer("changeItemBid|" + bidItem + "|" + String.valueOf(bidPrice) + "|" + user);

				bidError.setText("You have successfully purchased the item: " + bidItem);
				bidEntry.clear();
			}

			private void highestBidder(TextField bidEntry, Label bidError, String bidItem,
					Double bidPrice) {

				sendToServer("changeItemBid|" + bidItem + "|" + String.valueOf(bidPrice) + "|" + user);

				soundEffect(bidSound);

				bidError.setText("You are winning this item: " + bidItem);

				bidEntry.clear();
			}

			private void clearFields(Label error, Label bidErrors) {
				error.setText("");
				bidErrors.setText("");

				soundEffect(mouseSound);
			}

			private void lowBid(Label bidError) {
				bidError.setText("Error - Your bid is too low.");

				soundEffect(errorSound);
			}

			private void reserveNotMet(Label bidError) {
				bidError.setText("Error - Your bid does not meet reserve.");

				soundEffect(errorSound);
			}

			private void itemExpired(Label error, String item) {
				error.setText("Error - The auction for the item - " + item + " - has ended.");

				soundEffect(errorSound);
			}
		});

		add.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {

				String newItemStr = itemSelections.getValue();

				if (!watchNames.contains(newItemStr)) {

					soundEffect(watchAddSound);

					wlAddErr.setText("");
					bidError.setText("");

					AuctionItem newItem = null;

					for (AuctionItem i : auctionItems) {

						if (i.title.contentEquals(newItemStr)) {

							newItem = i;
						}
					}

					Label title = new Label(newItem.title);
					Label itemDesc = new Label(newItem.desc);
					String currentBidder = "none";

					initialItemDisplay(title, itemDesc);

					if (newItem.bidCurrent != 0.00) {
						// String currentBidder = new String();

						// currentBidder += "$" + decimalForm.format(newItem.bidCurrent);
						currentBidder = "$" + decimalForm.format(newItem.bidCurrent);
					}

					BigDecimal timer = newItem.timer;
					String timerString = timeFormat(timer);

					Label bidView = new Label(
							"Minimum Bid Price: $" + decimalForm.format(newItem.reserve) +
									"  Buy Now Price: $" + decimalForm.format(newItem.buyItNow) + "  Highest Bid: " +
									currentBidder + "  Highest Bidder Username: " + newItem.highestBidder);
					Label timerView = new Label(timerString);

					auctionInfo(bidView, timerView);

					HBox biddingHBox = new HBox(bidView, timerView);

					Label sellLabel = sellLabel(newItem);

					VBox itemDetailsVBox = new VBox(0, biddingHBox, sellLabel);
					itemDetailsVBox.setId("itemDetailsVBox");
					VBox.setMargin(sellLabel, new Insets(3, 0, 3, 0));

					Separator itemsSeparator = new Separator();

					VBox entireItemVBox = finalVbox(userWatchlist, newItemStr, title, itemDesc,
							itemDetailsVBox, itemsSeparator);

					watchNames.add(newItemStr);
					watchedItems.add(newItem);
					wlPairs.add(new Pair<String, VBox>(newItemStr, entireItemVBox));
				} else {
					wlAddErr.setText("The item - " + newItemStr + " - is already on your watchlist.");

					soundEffect(errorSound);
				}
			}

			private VBox finalVbox(ListView<VBox> wlView, String newItem, Label title,
					Label desc, VBox itemView, Separator itemSep) {
				VBox itemBox = new VBox(2, title, desc, itemView, itemSep);
				if (onWatch == false) {
					itemBox.setStyle("-fx-background-color: #F0FFFF;");
					onWatch = true;
				} else {
					itemBox.setStyle("-fx-background-color: #F0F8FF;");
					onWatch = false;
				}
				itemBox.setId(newItem);
				wlView.getItems().add(itemBox);
				return itemBox;
			}

			private Label sellLabel(AuctionItem itemAdded) {
				Label sellLabel = new Label(itemAdded.bidMessage);
				sellLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));
				sellLabel.setTextFill(Color.DARKBLUE);
				sellLabel.setId("sellLabel");
				return sellLabel;
			}

			private void auctionInfo(Label biddingLabel, Label timeLeftLabel) {
				biddingLabel.setFont(Font.font("Verdana", 11));
				biddingLabel.setTextFill(Color.DARKBLUE);
				biddingLabel.setId("biddingLabel");

				timeLeftLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
				timeLeftLabel.setTextFill(Color.DARKBLUE);
				timeLeftLabel.setId("timeLeftLabel");
			}

			private void initialItemDisplay(Label title, Label desc) {
				title.setFont(Font.font("Verdana", FontWeight.BOLD, 22));
				title.setTextFill(Color.DARKBLUE);

				desc.setFont(Font.font("Verdana", 11));
				desc.setTextFill(Color.DARKBLUE);
			}
		});

		logoff.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {

				soundEffect(exitSound);

				updatesComplete = true;
				for (Thread t : threadList) {
					try {
						t.join();
					} catch (InterruptedException e) {

						System.out.println("User logged out.");
					}
				}

				initializeDataFields();
				try {

					disconnectClientFromServer();
				} catch (IOException ex) {

					ex.printStackTrace();
				}

				screen.setTitle("Logon to eHills");

				screen.setScene(loginScene(screen));

				screen.show();
			}
		});

		remove.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {

				String itemToRemove = itemSelections.getValue();
				synchronized (wLock) {

					if (watchNames.remove(itemToRemove)
							&& watchedItems.removeIf(n -> (n.title.contentEquals(itemToRemove)))
							&& wlPairs.removeIf(n -> (n.getKey().equals(itemToRemove)))) {
						removeFromWatchlist(wlAddErr, bidError, userWatchlist, itemToRemove);
					} else {

						soundEffect(errorSound);
					}
				}
			}

			private void removeFromWatchlist(Label error, Label bidError, ListView<VBox> wlView, String itemToRemove) {

				soundEffect(watchRemoveSound);

				wlView.getItems().removeIf(n -> (n.getId().equals(itemToRemove)));

				error.setText("");
				bidError.setText("");
			}
		});

		Scene eCodeScene = new Scene(finalStack, Screen.getPrimary().getBounds().getMaxX(),
				Screen.getPrimary().getBounds().getMaxY());
		eCodeScene.getStylesheets().add("myStyle.css");
		return eCodeScene;
	}

	private static void startThreads(Thread notifications, Thread buttons, Thread watchlist) {
		notifications.start();
		threadList.add(notifications);
		buttons.start();
		threadList.add(buttons);
		watchlist.start();
		threadList.add(watchlist);
	}

	private static void auctionDisplay(Label watchlist, ListView<VBox> watchlistView, Label tabs,
			TabPane updated, VBox display) {
		display.setStyle("-fx-background-image: url(\"/lightSky.png\");");
		VBox.setMargin(watchlist, new Insets(0, 0, 0, 30));
		VBox.setMargin(watchlistView, new Insets(15, 30, 15, 30));
		VBox.setMargin(tabs, new Insets(0, 0, 0, 30));
		VBox.setMargin(updated, new Insets(15, 30, 15, 30));
	}

	private static void HBsetup(Label greetingLabel, Button logoff, HBox top, HBox middle,
			HBox bottom) {
		top.setAlignment(Pos.CENTER_RIGHT);
		HBox.setMargin(logoff, new Insets(12, 12, 0, 0));
		HBox.setMargin(greetingLabel, new Insets(9, 0, 0, 0));
		middle.setAlignment(Pos.BASELINE_CENTER);
		bottom.setAlignment(Pos.BASELINE_CENTER);
	}

	private static void tabFill(TabPane updates, ListView<String> purchased, ListView<String> bids) {
		bids.getStylesheets().add("list.css");
		purchased.getStylesheets().add("list.css");
		updates.getTabs().get(0).setContent(purchased);
		updates.getTabs().get(1).setContent(bids);
	}

	private static void tabSetup(Label tabs, TabPane updates, Tab sold, Tab bids) {
		tabs.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
		tabs.setTextFill(Color.BLUE);
		sold.setStyle("-tab-text-color: blue;");
		bids.setStyle("-tab-text-color: blue;");
		updates.getTabs().add(sold);
		updates.getTabs().add(bids);
	}

	private static ListView<VBox> watchingListView() {
		ListView<VBox> watchlist = new ListView<VBox>();
		watchlist.setPrefWidth(1900);
		watchlist.setPrefHeight(680);
		return watchlist;
	}

	private static Label watchlist() {
		Label watchlist = new Label("Your Watchlist:");
		watchlist.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
		watchlist.setTextFill(Color.BLUE);
		return watchlist;
	}

	private static Label bidError() {
		Label bidError = new Label();
		bidError.setFont(Font.font("Verdana", FontPosture.ITALIC, 13));
		bidError.setTextFill(Color.RED);
		return bidError;
	}

	private static Button placeBid() {
		Button bidButt = new Button("BID");
		bidButt.setPrefHeight(50);
		bidButt.setStyle("-fx-text-fill: blue; -fx-border-color: blue; font-weight: bold;");
		bidButt.setFont(Font.font("Verdana", 10));
		return bidButt;
	}

	private static TextField bidInput() {
		TextField bidEntry = new TextField();
		bidEntry.setPrefHeight(50);

		bidEntry.setStyle("-fx-text-inner-color: red;");
		bidEntry.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
		return bidEntry;
	}

	private static Label bidEntry() {
		Label bidlabel = new Label("Enter your bid: $");
		bidlabel.setFont(Font.font("Verdana", FontWeight.BOLD, 17));
		bidlabel.setTextFill(Color.BLUE);
		bidlabel.setAlignment(Pos.BASELINE_CENTER);
		return bidlabel;
	}

	private static Label watchlistError() {
		Label error = new Label();
		error.setFont(Font.font("Verdana", FontPosture.ITALIC, 13));
		error.setTextFill(Color.RED);
		return error;
	}

	private static Button removeButton() {
		Button remove = new Button("Remove from your watchlist");
		remove.setPrefHeight(50);
		remove.setStyle("-fx-text-fill: blue; -fx-border-color: blue; font-weight: bold;");
		remove.setFont(Font.font("Verdana", 10));
		return remove;
	}

	private static Button addButton() {
		Button add = new Button("Add to your watchlist");
		add.setPrefHeight(50);
		add.setStyle("-fx-text-fill: blue; -fx-border-color: blue; font-weight: bold;");
		add.setFont(Font.font("Verdana", 10));
		return add;
	}

	private static ChoiceBox<String> itemsDropDown() {
		ChoiceBox<String> itemSelection = new ChoiceBox<String>();
		itemSelection.setPrefHeight(50);
		itemSelection.setStyle("-fx-text-fill: blue; -fx-border-color: blue; font-weight: bold;");
		itemSelection.getItems().addAll(auctionNames);
		return itemSelection;
	}

	private static Label itemsMenuLbl() {
		Label itemMenu = new Label("Items:");
		itemMenu.setFont(Font.font("Verdana", FontWeight.BOLD, 11));
		itemMenu.setTextFill(Color.BLUE);
		return itemMenu;
	}

	private static Button auctionLogout() {
		Button logoff = new Button("LOGOUT");
		logoff.setStyle("-fx-text-fill: red; -fx-border-color: blue; font-weight: bold;");
		logoff.setFont(Font.font("Verdana", 15));
		logoff.setAlignment(Pos.CENTER_RIGHT);
		return logoff;
	}

	private static Label auctionGreeting() {
		Label greetingLabel = new Label(Client.user + ", welcome to the eHills auction!");
		greetingLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 30));
		greetingLabel.setTextFill(Color.BLUE);
		return greetingLabel;
	}

}