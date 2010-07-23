package press;

@SuppressWarnings("serial")
public class PressException extends RuntimeException {
    public PressException(Exception e) {
        super(e);
    }
    
    public PressException(String msg) {
        super(msg);
    }
}
