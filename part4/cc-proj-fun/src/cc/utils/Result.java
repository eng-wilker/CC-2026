package cc.utils;

import jakarta.ws.rs.WebApplicationException;

/**
 * 
 * Represents the result of an operation, either wrapping a result of the given type,
 * or an error.
 * 
 * @author smd
 *
 * @param <T> type of the result value associated with success
 */
public interface Result<T> {
	/**
	 * Tests if the result is an error.
	 */
	boolean isOK();
	
	/**
	 * Return the value of this result of throw exception
	 * @return the value of this result.
	 */
	T resultOrThrow();

	/**
	 * obtains the payload value of this result
	 * @return the value of this result.
	 */
	T value();

	/**
	 *
	 * obtains the error code of this result
	 * @return the error code
	 * 
	 */
	int error();
	
	/**
	 * Convenience method for returning non error results of the given type
	 * @param Class of value of the result
	 * @return the value of the result
	 */
	static <T> Result<T> ok( T result ) {
		return new OkResult<>(result);
	}

	/**
	 * Convenience method for returning non error results without a value
	 * @return non-error result
	 */
	static <T> OkResult<T> ok() {
		return new OkResult<>(null);	
	}
	
	/**
	 * Convenience method used to return an error 
	 * @return
	 */
	static <T> ErrorResult<T> error(int error) {
		return new ErrorResult<>(error);		
	}
	
	/**
	 * Convenience method used to return an error 
	 * @return
	 */
	static <T> ErrorResult<T> error(WebApplicationException e) {
		return new ErrorResult<>(e);		
	}
	
}

/*
 * 
 */
class OkResult<T> implements Result<T> {

	final T result;
	
	OkResult(T result) {
		this.result = result;
	}
	
	@Override
	public boolean isOK() {
		return true;
	}

	@Override
	public T value() {
		return result;
	}

	@Override
	public int error() {
		if( result == null)
			return 204;
		else
			return 200;
	}
	
	public String toString() {
		return "(OK, " + value() + ")";
	}

	@Override
	public T resultOrThrow() {
		return result;
	}
}

class ErrorResult<T> implements Result<T> {

	final int error;
	final WebApplicationException ex;
	
	ErrorResult(int error) {
		this.error = error;
		this.ex = null;
	}
	
	ErrorResult(WebApplicationException ex) {
		this.error = 500;
		this.ex = ex;
	}
	
	@Override
	public boolean isOK() {
		return false;
	}

	@Override
	public T value() {
		throw new RuntimeException("Attempting to extract the value of an Error: " + error());
	}

	@Override
	public int error() {
		return error;
	}
	
	public String toString() {
		return "(" + error() + ")";		
	}

	@Override
	public T resultOrThrow() {
		if( ex != null)
			throw ex;
		else
			throw new WebApplicationException( error);
	}
}