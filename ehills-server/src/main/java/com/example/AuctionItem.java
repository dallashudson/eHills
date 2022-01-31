package com.example;

import java.math.BigDecimal;

//AuctionItem Class
class AuctionItem {
	
	//Data Fields:
	protected double buyItNow;
	protected String title;
	protected BigDecimal timer;
	protected double bidCurrent;
	protected String desc;
	protected String bidMessage;
	protected double reserve;
	protected String highestBidder;

	
	//Methods: AuctionItem + Setters and Getters
	
	/**
	 * AuctionItem Constructor
	 * Called by the Server class
	 * Initializes all auction items with their corresponding information 
	 */
	protected AuctionItem (double buyItNow, String title, BigDecimal timer, String desc, double reserve) {
		this.buyItNow = buyItNow;
		this.title = title;
		this.timer = timer; 
		this.bidCurrent = 0.00; //initially, no bids 
		this.desc = desc;
		this.reserve = reserve;
		this.bidMessage = "Item is in stock.";
		this.highestBidder = "none";
	}
	
	protected double salePrice() {
		return buyItNow;
	}

	protected String getTitle() {
		return title;
	}

	protected BigDecimal getTimer() {
		return timer;
	}

	protected double getCurrentPrice() {
		return bidCurrent;
	}

	protected String getDesc() {
		return desc;
	}

	protected double getReserve() {
		return reserve;
	}

	protected String getBidMsg() {
		return bidMessage;
	}

	protected String getCurWinner() {
		return highestBidder;
	}

	protected void setSalePrice(double buyItNow) {
		this.buyItNow = buyItNow;
	}

	protected void setTitle(String title) {
		this.title = title;
	}

	protected void setTimer(BigDecimal timer) {
		this.timer = timer;
	}

	protected void setBidPrice(double bidPrice) {
		this.bidCurrent = bidPrice;
	}

	protected void setDesc(String desc) {
		this.desc = desc;
	}

	protected void setReserve(double reserve) {
		this.reserve = reserve;
	}

	protected void setBidMsg(String bidMessage) {
		this.bidMessage = bidMessage;
	}

	protected void setHighestBidder(String highest) {
		this.highestBidder = highest;
	}

}
