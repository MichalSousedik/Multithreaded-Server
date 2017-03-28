/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package psi_server;

import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class Server {

    private ServerSocket serverSocket;
    private Socket clientSocket;

    public Server(int port) {
        serverSocket = null;
        clientSocket = null;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.err.println("Could not listen on port: " + port + ".");
            System.exit(1);
        }

        findClient();

    }

    public void end() throws IOException {
        serverSocket.close();
    }

    private void findClient() {
        while (true) {
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                System.err.println("Accept failed.");
                System.exit(1);
            }
            System.out.println("client accepted from: " + clientSocket.getInetAddress()
                    + ":" + clientSocket.getPort());
            Thread t = new Thread(new Connection(serverSocket, clientSocket));
            t.start();
        }
    }

}

class Connection implements Runnable {

    private PrintWriter out;
    private BufferedReader in;
    private final ServerSocket serverSocket;
    private final Socket clientSocket;
    private String userName;
    private ClientMessageType nextExpected;
    private ClientMessageType interupted;
    private Map<Connection.ServerMessageType, String> serverResponse;

    public enum ClientMessageType {

        CLIENT_USER, CLIENT_PASSWORD, CLIENT_CONFIRM, CLIENT_RECHARGING, CLIENT_FULL_POWER, CLIENT_MESSAGE, UNSURE;
    }

    public enum ServerMessageType {

        SERVER_USER, SERVER_PASSWORD, SERVER_MOVE, SERVER_TURN_LEFT, SERVER_TURN_RIGHT, SERVER_PICK_UP, SERVER_OK, SERVER_LOGIN_FAILED, SERVER_SYNTAX_ERROR, SERVER_LOGIC_ERROR;
    }

    private void fillResponse() {
        serverResponse.put(ServerMessageType.SERVER_USER, "100 LOGIN\r\n");
        serverResponse.put(ServerMessageType.SERVER_PASSWORD, "101 PASSWORD\r\n");
        serverResponse.put(ServerMessageType.SERVER_MOVE, "102 MOVE\r\n	");
        serverResponse.put(ServerMessageType.SERVER_TURN_LEFT, "103 TURN LEFT\r\n");
        serverResponse.put(ServerMessageType.SERVER_TURN_RIGHT, "104 TURN RIGHT\r\n");
        serverResponse.put(ServerMessageType.SERVER_PICK_UP, "105 GET MESSAGE\r\n");
        serverResponse.put(ServerMessageType.SERVER_OK, "200 OK\r\n");
        serverResponse.put(ServerMessageType.SERVER_LOGIN_FAILED, "300 LOGIN FAILED\r\n");
        serverResponse.put(ServerMessageType.SERVER_SYNTAX_ERROR, "301 SYNTAX ERROR\r\n");
        serverResponse.put(ServerMessageType.SERVER_LOGIC_ERROR, "300 LOGIN FAILED\r\n");
    }

    public Connection(ServerSocket serverSocket, Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.serverSocket = serverSocket;
        this.userName = "";
        serverResponse = new HashMap<>();
        fillResponse();
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(
                    new InputStreamReader(
                            clientSocket.getInputStream()));
        } catch (IOException e) {
            System.err.println("Couldn't get I/O.");
            System.exit(1);
        }

    }

    private void moveClient() {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private boolean passwdValid(String text) {
        int sumUsername = 0;
        int sumPasswd = Integer.parseInt(text);

        //podivat se na ASCII 67 neni dobre
        for (int i = 0; i < userName.length(); i++) {
            sumUsername += (int) userName.charAt(i);
        }
        //podivat se na ASCII 67 neni dobre          

        return (sumPasswd == sumUsername);

    }

    private String reactOnMessage(String text, ClientMessageType type) {
        if (text.equals("ERROR_IO") || text.equals("ERROR_LENGTH") || text.equals("ERROR_SYNTAX")) {
            return serverResponse.get(Connection.ServerMessageType.SERVER_SYNTAX_ERROR);
        }

        if (text.equals("ERROR_LOGIC")) {
            return serverResponse.get(Connection.ServerMessageType.SERVER_LOGIC_ERROR);
        }

        switch (type) {

            case CLIENT_USER: {
                userName = text;
            }
            break;

            case CLIENT_PASSWORD: {
                if (!passwdValid(text)) {
                    return serverResponse.get(Connection.ServerMessageType.SERVER_LOGIN_FAILED);
                }
                return serverResponse.get(Connection.ServerMessageType.SERVER_OK);
            }

            case CLIENT_CONFIRM: {
                //zkontroloval pozici robota a podle toho bud posunout, nebo nechat vyzvednout vzkaz
                //podivat se jestli nepotrebuje dobit
            }
            break;

            case CLIENT_MESSAGE: {
                //precist tajnou zpravu
                //pokud nejsme na (0,0) znicit robota a ukoncit komunikaci
            }
            break;

            case CLIENT_RECHARGING: {
                //timeout 
                //dalsi ocekavame CLIENT_FULL
                return reactOnMessage(validateMessage(getMessage(ClientMessageType.CLIENT_FULL_POWER), ClientMessageType.CLIENT_FULL_POWER), ClientMessageType.CLIENT_FULL_POWER);
            }

            case CLIENT_FULL_POWER: {
                //pokracovat v predeslych akcich
                return reactOnMessage(validateMessage(getMessage(interupted), interupted), interupted);

            }

            default:
                break;
        }
        return ";";
    }

    private String getMessage(Connection.ClientMessageType expected) {
        System.out.println("1");
        String text = "";
        int tmp;
        while (!text.endsWith("\r\n")) {
            System.out.println("3");
            try {
                tmp = in.read();

                System.out.println(tmp);
                    System.out.println("6");
                if (tmp == -1) {
                    System.out.println("4");
                    return ";";
                }

                //na zaklade ocekavane odpovedi kontrolovat jestli vstup dava smysl
                text += (char) tmp;
                if ((text.length() > 100 && (expected == Connection.ClientMessageType.CLIENT_USER)
                        || (expected == Connection.ClientMessageType.CLIENT_MESSAGE))
                        || (text.length() > 12 && (expected == Connection.ClientMessageType.CLIENT_CONFIRM)
                        || (expected == Connection.ClientMessageType.CLIENT_RECHARGING)
                        || (expected == Connection.ClientMessageType.CLIENT_FULL_POWER))
                        || (text.length() > 7 && (expected == Connection.ClientMessageType.CLIENT_PASSWORD))) {
                    return "ERROR_LENGTH";
                }

            } catch (IOException e) {
                System.out.println("ERROR_IO");
                return "ERROR_IO";
            }

        }
        System.out.println("2");
        return text;
    }

    private String validateMessage(String text, Connection.ClientMessageType expected) {
        if (text.startsWith("ERROR")) {
            return text;
        }

        if (text.equals("RECHARGING\r\n")) {
            nextExpected = ClientMessageType.CLIENT_FULL_POWER;
            interupted = expected;
            expected = ClientMessageType.CLIENT_RECHARGING;
        }

        if (nextExpected == ClientMessageType.CLIENT_FULL_POWER) {

            if (!(text.equals("FULL POWER\r\n"))) {
                return "ERROR_LOGIC";
            }

        }

        switch (expected) {
            //zkontroluj syntaxi
            case CLIENT_CONFIRM: {
                if (!text.startsWith("OK")) {
                    return "ERROR_SYNTAX";
                }
            }
            break;

            case CLIENT_FULL_POWER: {
                if (!text.equals("FULL POWER\r\n")) {
                    return "ERROR_SYNTAX";
                }
            }
            break;
            case CLIENT_RECHARGING: {
                if (!text.startsWith("RECHARGING\r\n")) {
                    return "ERROR_SYNTAX";
                }
            }
            break;

            default:
                return "ERROR_SYNTAX";

        }

        return text.substring(0, text.indexOf("\r\n"));
    }

    public boolean serverUser() {
        out.print(serverResponse.get(Connection.ServerMessageType.SERVER_USER));
        out.flush();
        String response = reactOnMessage(validateMessage(getMessage(ClientMessageType.CLIENT_USER), ClientMessageType.CLIENT_USER), ClientMessageType.CLIENT_USER);
        System.out.println(userName);
        if (response.startsWith("ERROR")) {
            return false;
        }
        return true;
    }

    public boolean serverPassword() {
        out.print(serverResponse.get(Connection.ServerMessageType.SERVER_PASSWORD));
        out.flush();
        String response = reactOnMessage(validateMessage(getMessage(ClientMessageType.CLIENT_PASSWORD), ClientMessageType.CLIENT_PASSWORD), ClientMessageType.CLIENT_PASSWORD);
        out.print(response);
        out.flush();
        if (response.equals("SERVER_LOGIN_FAILED") || response.startsWith("ERROR")) {
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        String response = "";

        if (!serverUser()) {
            response = "ERROR_USER";
        }
        if (!serverPassword()) {
            response = "ERROR_PASSWORD";
        } else {
            /* moveClient();
             response = reactOnMessage(validateMessage(getMessage(ClientMessageType.CLIENT_CONFIRM), ClientMessageType.CLIENT_CONFIRM), ClientMessageType.CLIENT_CONFIRM);
             moveClient();
             response = reactOnMessage(validateMessage(getMessage(ClientMessageType.CLIENT_CONFIRM), ClientMessageType.CLIENT_CONFIRM), ClientMessageType.CLIENT_CONFIRM);
             */
        }

        /*while (clientSocket.isConnected() && (!(response.startsWith("ERROR")))) {
         //response = makeDecision(parseOrder(readMsg(in)));
         if (!response.startsWith("ERROR")) {
         out.print(response);
         out.flush();
         }
         }*/
        //close connenction
        out.close();

        System.out.println("server ending");

        try {
            out.close();
            in.close();
            clientSocket.close();
        } catch (IOException ex) {
            System.err.println("ERROR_CLOSING");
        }

    }

}

public class PSI_server {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {

        Server test = new Server(3999);
        test.end();

        /*
         String inputLine, outputLine;
         while ((inputLine = in.readLine()) != null) {
         System.out.println("request: " + inputLine);
         outputLine = inputLine.toUpperCase();
         out.println(outputLine);
         }
         */
    }

}
