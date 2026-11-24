package bo.uagrm.bolsa.mail;

public class MailFormatter {

    private MailFormatter() {}

    public static String formatSuccess(String message) {
        return "EXITO: " + message;
    }

    public static String formatError(String message) {
        return "ERROR: " + message;
    }

    public static String wrapHtml(String title, String content, boolean success) {
        return "<html>\n"
            + "<body style=\"font-family: Arial, sans-serif; padding: 15px; color: #000000; background-color: #ffffff;\">\n"
            + "  <h3 style=\"margin-top: 0; color: #000000; border-bottom: 1px solid #777777; padding-bottom: 5px; font-size: 16px;\">" + title + "</h3>\n"
            + "  <div style=\"margin-top: 10px; line-height: 1.4;\">\n"
            + "    " + content + "\n"
            + "  </div>\n"
            + "</body>\n"
            + "</html>";
    }

    public static String formatSuccessHtml(String title, String message) {
        String content = "<p style=\"color: #008000; font-weight: bold; margin: 0 0 10px 0;\">EXITO</p>\n"
            + "<p style=\"margin: 0; font-size: 13px;\">" + message.replace("\n", "<br>\n") + "</p>\n";
        return wrapHtml(title, content, true);
    }

    public static String formatErrorHtml(String title, String message) {
        String content = "<p style=\"color: #cc0000; font-weight: bold; margin: 0 0 10px 0;\">ERROR</p>\n"
            + "<p style=\"margin: 0; font-size: 13px;\">" + message.replace("\n", "<br>\n") + "</p>\n";
        return wrapHtml(title, content, false);
    }
}
