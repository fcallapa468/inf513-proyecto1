package bo.uagrm.bolsa.commands;

public class CommandResponse {

    private final boolean success;
    private final String message;
    private final String htmlMessage;

    public CommandResponse(boolean success, String message) {
        this(success, message, null);
    }

    public CommandResponse(boolean success, String message, String htmlMessage) {
        this.success = success;
        this.message = message;
        this.htmlMessage = htmlMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getHtmlMessage() {
        return htmlMessage != null ? htmlMessage : message;
    }
}
