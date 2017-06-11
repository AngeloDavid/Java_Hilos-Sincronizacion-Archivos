package filesync;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author edwinsp
 * @date 8th April 2013
 */
public class SyncServer {

	/**
	 * @param args the command line arguments
	 */	
	public static void main(String[] args) {
            int serverPort = 7899; // the server port
            try {
                ServerSocket listenSocket = new ServerSocket(serverPort);
                int i = 0;
                while(true) {
                if( args.length != 1 ){
			System.out.println("Usage: java -jar syncserver.jar filename");
			System.exit(-1);
		}
                    System.out.println("esperando conexxion");
                Socket clientSocket = listenSocket.accept();
                i++;
		String filename = args[0];
                
		SyncServerThread c = new SyncServerThread( filename,clientSocket );
		c.start();
                }
            } catch (IOException ex) {
                Logger.getLogger(SyncServer.class.getName()).log(Level.SEVERE, null, ex);
            }
		
	}
}
