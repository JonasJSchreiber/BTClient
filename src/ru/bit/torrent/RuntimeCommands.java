package ru.bit.torrent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class RuntimeCommands implements Runnable {
	
	private Manager manager;
	
	public RuntimeCommands(Manager manager) {
		this.manager = manager;
	}

	public void run() {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String userInput = "";
		try {
			userInput = br.readLine();
			while (!userInput.equals(null) && !userInput.equals("quit"))
			{
				userInput = br.readLine();
			}	
			manager.exit();
			return;
		} catch (IOException e) {
//			e.printStackTrace();
		} catch (InterruptedException e) {
//			e.printStackTrace();
		}
		
	}
}
