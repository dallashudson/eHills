package com.example;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

class DatabaseConnection {

	private ArrayList<AuctionItem> auctionList = new ArrayList<AuctionItem>();

	protected Connection connect() {

		String urlName = "jdbc:sqlite:" + Server.DBHome;
		Connection connection = null;
		try {

			connection = DriverManager.getConnection(urlName);
			connection.setAutoCommit(false);
		} catch (SQLException ex) {
			System.out.println(ex.getMessage());
		}
		return connection;
	}

	protected ArrayList<AuctionItem> collectAuctionItems() {

		String fields = "SELECT ItemName, ItemDescription, MinimumPrice, BuyNow, TimeLimit FROM Items_Table";

		try (

				Connection connection = this.connect();

				Statement statement = connection.createStatement();

				ResultSet resultSet = statement.executeQuery(fields)) {

			while (resultSet.next()) {
				double buyItNow = resultSet.getDouble("BuyNow");
				String title = resultSet.getString("ItemName");
				BigDecimal timer = new BigDecimal(resultSet.getDouble("TimeLimit"));
				String desc = resultSet.getString("ItemDescription");
				double reserve = resultSet.getDouble("MinimumPrice");

				AuctionItem itemInQuestion = new AuctionItem(buyItNow, title, timer, desc, reserve);
				auctionList.add(itemInQuestion);
			}

		} catch (SQLException ex) {
			System.out.println(ex.getMessage());
		}

		return auctionList;
	}

}
