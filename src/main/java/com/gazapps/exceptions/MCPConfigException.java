package com.gazapps.exceptions;
public  class MCPConfigException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public MCPConfigException(String message) {
        super(message);
    }
    
    public MCPConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
