package cl.duocuc.pedidos360.mqadmin.dto;

/** Expresiones regulares de validación para nombres de recursos de RabbitMQ. */
public final class NamePatterns {

    /** Letras, números, punto, guion y guion bajo (1 a 255 caracteres). */
    public static final String NAME = "^[A-Za-z0-9._-]{1,255}$";

    /** Igual que NAME, más * y # (comodines de los exchanges topic); puede ir vacía (fanout). */
    public static final String ROUTING_KEY = "^[A-Za-z0-9._*#-]{0,255}$";

    public static final String NAME_MESSAGE =
            "solo puede contener letras, números, punto, guion y guion bajo (máximo 255 caracteres)";

    public static final String ROUTING_KEY_MESSAGE =
            "solo puede contener letras, números, punto, guion, guion bajo, * y # (máximo 255 caracteres)";

    private NamePatterns() {
    }
}
