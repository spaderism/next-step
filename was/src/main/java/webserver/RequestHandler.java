package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import db.DataBase;
import model.User;
import util.HttpRequestUtils;
import util.HttpRequestUtils.Pair;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (
        		InputStream in = connection.getInputStream(); 
        		InputStreamReader isr = new InputStreamReader(in);
        		BufferedReader br = new BufferedReader(isr);
        		OutputStream out = connection.getOutputStream();
        ) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
        	String httpHeaderFirstLine = "";
        	Map<String, String> httpHeaderMap = new HashMap<>();
        	String line;
        	
        	while((line = br.readLine()) != null && !"".equals(line)) {
        		Object object = HttpRequestUtils.parseHeader(line);
        		
        		if (object != null) {
        			Pair pair = (Pair)object;
        			
        			httpHeaderMap.put(pair.getKey(), pair.getValue());
        			
        			log.debug(pair.toString());
        		} else {
        			httpHeaderFirstLine = line;
        			
        			log.debug(httpHeaderFirstLine);
        		}
        	}
        	
        	DataOutputStream dos = new DataOutputStream(out);
        	
        	String[] httpHeaderFirstLines = httpHeaderFirstLine.split(" ");
        	String method = httpHeaderFirstLines[0];
        	String url = httpHeaderFirstLines[1];

        	Map<String, String> dataMap = null;
        	Map<String, String> cookieMap = null;
        	
        	int index = url.indexOf("?");
        	String path = url.substring(0, index);
        	String data = null;
        	
        	
        	if (method.equals("GET")) {
        		data = url.substring(index + 1);
        	} else {
        		int contentLength = Integer.parseInt(httpHeaderMap.get("Content-Length"));
    			data = util.IOUtils.readData(br, contentLength);
        	}
        	
        	dataMap = util.HttpRequestUtils.parseQueryString(data);
        	
        	if (url.startsWith("/user/create")) {
        		DataBase.addUser(new User(
    				dataMap.get("userId"), dataMap.get("password"),
    				dataMap.get("name"), dataMap.get("email")
        		));
        		
        		response302Header(dos, httpHeaderMap.get("Origin") + "/index.html", null);
        	} else if (url.startsWith("/user/login") && method.equals("POST")) {
        		if ((User)DataBase.findUserById(dataMap.get("userId")) != null || DataBase.findUserById(dataMap.get("userId")).getPassword().equals(dataMap.get("password"))) {
        			String cookie = "Set-Cookie: logined=true";
        			
        			response302Header(dos, httpHeaderMap.get("Origin") + "/index.html", cookie);
        		} else {
        			String cookie = "Set-Cookie: logined=false";
        			
        			response302Header(dos, httpHeaderMap.get("Origin") + "/user/login_failed.html", cookie);
        		}
        	} else if (url.startsWith("/user/list") && method.equals("GET")) {
        		cookieMap = HttpRequestUtils.parseCookies(httpHeaderMap.get("Cookie"));
        		
        		Boolean loginCookie = Boolean.parseBoolean(cookieMap.get("logined"));
        		
        		if (!loginCookie) {
        			String cookie = "Set-Cookie: logined=false";
        			
        			response302Header(dos, httpHeaderMap.get("Origin") + "/user/login.html", cookie);
        		}
        		
        		if ("true".equals(loginCookie)) {
        			
        		}
        	} else {
        		byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                
                response200Header(dos, body.length);
                responseBody(dos, body);
        	}
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
    
    private void response302Header(DataOutputStream dos, String redirectUrl, String cookie) {
    	try {
    		dos.writeBytes("HTTP/1.1 302 Redirect \r\n");
    		
    		if (cookie != null) {
    			dos.writeBytes(cookie);
    		}
    		
    		dos.writeBytes("Location: " + redirectUrl + "\r\n");
    		dos.writeBytes("\r\n");
    	} catch (IOException e) {
    		log.error(e.getMessage());
    	}
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
