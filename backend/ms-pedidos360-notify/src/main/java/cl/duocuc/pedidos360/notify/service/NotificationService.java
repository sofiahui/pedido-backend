package cl.duocuc.pedidos360.notify.service;

import cl.duocuc.pedidos360.notify.exception.NonRecoverableCommandException;
import cl.duocuc.pedidos360.notify.messaging.CommandEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Tareas que ejecuta notify. Por ahora simulan el envío con logs
 * (no hay servidor de correo, impresora ni generador de PDF reales).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void sendEmail(CommandEnvelope cmd) {
        simulateFailureIfRequested(cmd);
        log.info("EMAIL enviado al cliente {}: pedido {} ahora está {}",
                cmd.payload().get("customerId"), cmd.payload().get("orderId"), cmd.payload().get("status"));
    }

    public void printKitchenTicket(CommandEnvelope cmd) {
        simulateFailureIfRequested(cmd);
        log.info("TICKET DE COCINA impreso para el pedido {}", cmd.payload().get("orderId"));
    }

    public void generateInvoice(CommandEnvelope cmd) {
        simulateFailureIfRequested(cmd);
        log.info("BOLETA generada para el pedido {}", cmd.payload().get("orderId"));
    }

    /**
     * Solo para demostrar el manejo de errores. Si el payload trae
     * "simulateError": "temporal" o "permanente", se lanza el error correspondiente.
     */
    private void simulateFailureIfRequested(CommandEnvelope cmd) {
        Object sim = cmd.payload().get("simulateError");
        if ("temporal".equals(sim)) {
            throw new IllegalStateException("Servicio externo no disponible (simulado)");
        }
        if ("permanente".equals(sim)) {
            throw new NonRecoverableCommandException("Datos del comando inválidos (simulado)");
        }
    }
}