package FinalServer;

import java.util.Observable;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.net.Socket;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Observer;

class ClientHandler implements Runnable, Observer {

	protected int clientID = -1;
	private Server api;
	protected Socket cliSock;
	protected BufferedReader apiCall;
	protected PrintWriter apiReturn;

	protected ClientHandler(Server api, Socket cliSock, int clientID) {
		this.api = api;
		this.cliSock = cliSock;
		this.clientID = clientID;
		try {

			apiProcess();

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void apiProcess() throws IOException {
		apiReturn = new PrintWriter(this.cliSock.getOutputStream());
		apiCall = new BufferedReader(new InputStreamReader(this.cliSock.getInputStream()));
	}

	protected void sendToClient(String apiCall) {
		apiReturn.println(apiCall);
		apiReturn.flush();
	}

	@Override
	public void run() {
		String apiCommand;
		try {
			while ((apiCommand = apiCall.readLine()) != null) {

				System.out.println("Client API Call: " + apiCommand);

				if (apiCommand.contentEquals("setUpAuctionItems") || apiCommand.contentEquals("disconnectClient")) {
					apiCommand = apiCommand + " " + clientID;
				}
				api.processRequest(apiCommand);
			}
		} catch (IOException ex) {

		} finally {

			System.out.println("Socket got disconnected: #" + clientID + ".");
		}
	}

	public void update(Observable o, Object arg) {
		this.sendToClient((String) arg);
	}
}