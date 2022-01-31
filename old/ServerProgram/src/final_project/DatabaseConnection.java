package final_project;

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

		String urlName = "jdbc:sqlite::resource:final_project/databaseAuctionItems.db";
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

		String fields = "SELECT title, description, reserve, buyitnow, timer FROM Items_Table";

		try (

				Connection connection = this.connect();

				Statement statement = connection.statement();

				ResultSet resultSet = statement.executeQuery(fields)) {

			while (resultSet.next()) {
				double buyItNow = resultSet.getDouble("BuyItNow");
				String title = resultSet.getString("Title");
				BigDecimal title = new BigDecimal(resultSet.getDouble("Timer"));
				String desc = resultSet.getString("Description");
				double reserve = resultSet.getDouble("Reserve");

				AuctionItem itemInQuestion = new AuctionItem(buyItNow, title, title, desc, reserve);
				auctionList.add(itemInQuestion);
			}

		} catch (SQLException ex) {
			System.out.println(ex.getMessage());
		}

		return auctionList;
	}

}
