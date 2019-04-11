/*
 * Created on 22/04/2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.midas.ms.trader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.midas.metainfo.ContainerInfo;
import org.midas.ms.catalog.Catalog;
import org.midas.ms.catalog.CatalogException;
import org.midas.ms.manager.execution.Logger;
import org.midas.ms.manager.manager.Manager;
import org.midas.ms.manager.manager.ManagerScreen;
import org.midas.ms.trader.webservices.Generator;



/**
 * @author Administrador
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class Trader extends HttpServlet
{	
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
	{
		doPost(req,res);
	}	
	
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
	{
		// Recuperando Tipo da Requisi��o
		String requisitionType = req.getParameter("type");
		
		// Validando Requisi��o
		if (requisitionType == null)
		{
			throw new ServletException("Invalid Requisition - Expected parameter 'type' with requisition type {register,verify,provide,ping}");
		}
		
		// Processando Requisi��o
		if (requisitionType.equals("register"))
		{
			try
			{
				registerRequest(req,res);
			}
			catch (CatalogException e)
			{
				e.printStackTrace();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			catch (ClassNotFoundException e)
			{
				e.printStackTrace();
			}
		}
		else if (requisitionType.equals("provide"))
		{
			provideRequest(req,res);
		}
		else if (requisitionType.equals("ping"))
		{
			pingRequest(req,res);
		}
		else if (requisitionType.equals("verify"))
		{
			verifyRequest(req,res);
		}
		else
		{
			throw new IOException("Invalid Requisition - Expected parameter 'type' with requisition type {register,verify,provide,ping}");
		}
	}
	
	private void pingRequest(HttpServletRequest req, HttpServletResponse res) throws IOException
	{
		ObjectOutputStream out = new ObjectOutputStream(res.getOutputStream());			
		out.writeUTF("pong...");
		out.close();
	}
	
	private void verifyRequest(HttpServletRequest req, HttpServletResponse res) throws IOException
	{
		String  service;
		String  organization;
		
		// Recuperando Entrada da Requisi��o
		ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(req.getInputStream()));
			
		organization = ois.readUTF();
		service		 = ois.readUTF();
			
		ois.close();
		
		// Processando Resposta
		res.setStatus(HttpServletResponse.SC_OK);
		
		ObjectOutputStream oos = new ObjectOutputStream(res.getOutputStream());					
						
		oos.writeBoolean(Catalog.hasService(organization,service));
		
		oos.flush();
		oos.close();
	}

	private void provideRequest(HttpServletRequest req, HttpServletResponse res) throws IOException
	{		
		// Servi�o
		HashMap in;
		String  service;
		String  organization;
		
		// QoS
		long   startTime;
		long   endTime;
		String requirer;
		String provider;
		
		// Resultado
		String resultType = null;
		Object resultObject =  null;
		
		// Conex�o HTTP
		HttpURLConnection uc = null;
		ContainerInfo container = null;
		
		// Marcando tempo de in�cio 
		Manager.increaseThreadCount();
		startTime = System.currentTimeMillis();
		
		// Recuperando Entrada da Requisi��o
		try
		{
			ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(req.getInputStream()));
			
			requirer	 = ois.readUTF();
			organization = ois.readUTF();
			service		 = ois.readUTF();			
			in			 = (HashMap)ois.readObject();		
		}		
		catch (ClassNotFoundException e)
		{
			// Recuperando resposta do container alvo 
			ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(res.getOutputStream()));
			
			oos.writeUTF("error");
			String message = "Could not recover Map object with parameters from connection stream";
			oos.writeObject("Mas Server: "+message);
			
			oos.flush();
			oos.close();
			
			// Marcando tempo de t�rmino
			Manager.decreaseThreadCount();
			endTime = System.currentTimeMillis();
			
			
			// Logando Resultados
			Logger.addEntry(" unknow service - Timing: "+(endTime-startTime)+"ms - "+
							"\nRequirer: not found -  Provider: not found | "+message,false);
			
			return;
		}
		
		// Localizando Container Adequado
		try
		{
			container = Catalog.getContainerByService(organization,service);
			provider  = container.getName();
			
			// Requisitando Servi�o ao Container
			
			URL url; 
			
			if (container.getType().equals("as"))
			{
				url = new URL("http://"+container.getContainerAddress()+":"+container.getContainerPort()+"/agentserver/broker/receiver?type=provide");
			}
			else
			{
				url = new URL("http://"+container.getContainerAddress()+":"+container.getContainerPort()+"/enterpriseserver/broker/receiver?type=provide");
			}
			
			uc  = (HttpURLConnection)url.openConnection();
				
			uc.setRequestProperty("Content-Type", "application/octet-stream");
			
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setUseCaches(false);
									         
			// Enviando requisi��o ao container
			ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(uc.getOutputStream()));		    
			out.writeUTF(organization);
			out.writeUTF(service);
			out.writeObject(in);
			
			out.flush();
			out.close();				
		} 
		catch (MalformedURLException e) 
		{		
			// Esta exce��o � lan�ada caso a URL definida pelo container alvo no MS seja inv�lida 
			ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(res.getOutputStream()));
			
			oos.writeUTF("error");
			String message = "Could not process provide request, unable to compose a valid container URL";
			oos.writeObject("Mas Server: "+message);
			
			oos.flush();
			oos.close();
			
			// Marcando tempo de t�rmino
			Manager.decreaseThreadCount();
			endTime = System.currentTimeMillis();
			
			// Logando Resultados
			Logger.addEntry(" "+organization+"."+service+" - Timing: "+(endTime-startTime)+"ms - "+
							"\nRequirer: "+requirer+"  -  Provider: not found | "+message,false);
			
			return;			
		}
		catch (IOException e) 
		{			
			// Esta exce��o � lan�ada caso hajam falhas de comunica��o entre o MS e o container alvo 
			String message;
			ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(res.getOutputStream()));
			
			oos.writeUTF("error");
			message = "Could not process provide request, unable to communicate with target container. Perhaps it is offline or out of synchorinzation";
			oos.writeObject("Mas Server: "+message);
			
			oos.flush();
			oos.close();
			
			// Marcando tempo de t�rmino
			Manager.decreaseThreadCount();
			endTime = System.currentTimeMillis();
			
			// Logando Resultados
			Logger.addEntry(" "+organization+"."+service+" - Timing: "+(endTime-startTime)+"ms - "+
							"\nRequirer: "+requirer+"  -  Provider: not found | "+message,false);
			
			return;
		} 
		catch (CatalogException e)
		{
			// Recuperando resposta do container alvo e repassando ao container pedinte 
			String message;
			ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(res.getOutputStream()));
			
			oos.writeUTF("error");
			
			
			if (container == null)
			{
				message = "Could not process provide request, could not find a container able to provide the service '"+service+"' for a organization'"+organization+"'";
				oos.writeObject("Mas Server: "+message);
			}
			else
			{
				message = "Could not process provide request, internal error due to inconsistance on meta-information about the container '"+container+"'";
				oos.writeObject("Mas Server: "+message);
			}
			
			oos.flush();
			oos.close();	
			
			// Marcando tempo de t�rmino
			Manager.decreaseThreadCount();
			endTime = System.currentTimeMillis();
			
			// Logando Resultados
			Logger.addEntry(" "+organization+"."+service+" - Timing: "+(endTime-startTime)+"ms - "+
							"\nRequirer: "+requirer+"  -  Provider: not found | "+message,false);
			
			return;
		}
				
		try
		{
			// Recuperando resposta do container alvo 
			ObjectInputStream ois =	new ObjectInputStream(new BufferedInputStream(uc.getInputStream()));
			
			resultType = ois.readUTF();
			resultObject = ois.readObject();
			
			ois.close();
			uc.disconnect();
		}
		catch (ClassNotFoundException e)
		{
			// Recuperando resposta do container alvo 
			ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(res.getOutputStream()));
			
			oos.writeUTF("error");
			oos.writeObject("Mas Server: Could not process provide request, target container tried to send back an object with unknown class type");
			
			oos.flush();
			oos.close();
			
			// Marcando tempo de t�rmino
			Manager.decreaseThreadCount();
			endTime = System.currentTimeMillis();
			
			// Logando Resultados
			Logger.addEntry(" "+organization+"."+service+" - Timing: "+(endTime-startTime)+"ms - "+
							"\nRequirer: "+requirer+"  -  Provider: "+provider+" | Could not process provide request, target container tried to send back an object with unknown class type",false);
			
			return;
		}		
		
		// Repassando resposta ao container requerente
		ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(res.getOutputStream()));
			
		oos.writeUTF(resultType);
		oos.writeObject(resultObject);
	
		oos.flush();
		oos.close();
		
		// Marcando tempo de t�rmino
		Manager.decreaseThreadCount();
		endTime = System.currentTimeMillis();
		
		if (resultType.equals("success"))
		{
			// Logando Resultados
			Logger.addEntry(" "+organization+"."+service+" - Timing: "+(endTime-startTime)+"ms - "+
							"\nRequirer: "+requirer+"  -  Provider: "+provider,true);
		}
		else
		{
			String message;
			
			try
			{
				message = ((Exception)resultObject).getMessage();
			}
			catch (ClassCastException e)
			{
				message = resultObject.toString();
			}
			
			
			// Logando Resultados
			Logger.addEntry(" "+organization+"."+service+" - Timing: "+(endTime-startTime)+"ms - "+
							"\nRequirer: "+requirer+"  -  Provider: "+provider+" | "+message,false);
		}
		
		uc.disconnect();
	}

	private void registerRequest(HttpServletRequest req, HttpServletResponse res) throws IOException, ClassNotFoundException, CatalogException
	{							
		Object data;
		
		// Recuperando Entrada da Requisi��o
		ObjectInputStream  in  = new ObjectInputStream(new BufferedInputStream(req.getInputStream()));
		data = in.readObject();
		
		in.close();
		
		// Construindo Resposta da Requisi��o
		res.setStatus(HttpServletResponse.SC_OK);
	      
		ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(res.getOutputStream()));
	    
		oos.writeUTF("registered");
	    oos.flush();
	    oos.close();
		
	    // Tratando Objeto Recebido
	    // TODO: Delegar responsabilidade de notifica��o do ManagerScreen ao Catalog
	    Catalog.addContainer((ContainerInfo)data);
	    Generator.GenerateWSDL((ContainerInfo)data);
	    Logger.addEntry("New container successfully registered - "+((ContainerInfo)data).getName());
		ManagerScreen.userInterfaceEvent("Refresh Services");
	}

	public static boolean ping(ContainerInfo container)
	{	
		try
		{			
			URL url = new URL("http://"+container.getContainerAddress()+":"+container.getContainerPort()+"/agentserver/broker/receiver?type=ping");									
			HttpURLConnection uc  = (HttpURLConnection)url.openConnection();
				
			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setUseCaches(false);
										
			ObjectInputStream in = new ObjectInputStream(uc.getInputStream());					
			in.close();
						
			uc.disconnect();
			
			return true;
		}
		catch (MalformedURLException e) 
		{		
			e.printStackTrace();
			return false;
		}
		catch (IOException e) 
		{			
			e.printStackTrace();
			return false;
		} 	
	}
}