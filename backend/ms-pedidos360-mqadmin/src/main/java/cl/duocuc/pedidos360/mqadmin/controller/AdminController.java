package cl.duocuc.pedidos360.mqadmin.controller;

import cl.duocuc.pedidos360.mqadmin.dto.BindingRequest;
import cl.duocuc.pedidos360.mqadmin.dto.BindingResponse;
import cl.duocuc.pedidos360.mqadmin.dto.ExchangeRequest;
import cl.duocuc.pedidos360.mqadmin.dto.ExchangeResponse;
import cl.duocuc.pedidos360.mqadmin.dto.NamePatterns;
import cl.duocuc.pedidos360.mqadmin.dto.QueueRequest;
import cl.duocuc.pedidos360.mqadmin.dto.QueueResponse;
import cl.duocuc.pedidos360.mqadmin.service.RabbitAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints REST del administrador de RabbitMQ. Solo el rol Admin puede usarlos. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administrador de RabbitMQ", description = "Crear y eliminar colas, exchanges y bindings")
public class AdminController {

    private final RabbitAdminService adminService;

    public AdminController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    // ---- colas ----

    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una cola (opcionalmente con su DLQ)")
    public QueueResponse createQueue(@Valid @RequestBody QueueRequest request) {
        return adminService.createQueue(request);
    }

    @GetMapping("/queues/{name}")
    @Operation(summary = "Consulta una cola: mensajes listos y consumidores")
    public QueueResponse getQueue(
            @PathVariable @Pattern(regexp = NamePatterns.NAME, message = "name " + NamePatterns.NAME_MESSAGE) String name) {
        return adminService.getQueue(name);
    }

    @DeleteMapping("/queues/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una cola (las colas q.cmd.* del sistema están protegidas)")
    public void deleteQueue(
            @PathVariable @Pattern(regexp = NamePatterns.NAME, message = "name " + NamePatterns.NAME_MESSAGE) String name) {
        adminService.deleteQueue(name);
    }

    // ---- exchanges ----

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un exchange direct, topic o fanout")
    public ExchangeResponse createExchange(@Valid @RequestBody ExchangeRequest request) {
        return adminService.createExchange(request);
    }

    @DeleteMapping("/exchanges/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina un exchange (cmd.direct, cmd.topic y cmd.dead.dlx están protegidos)")
    public void deleteExchange(
            @PathVariable @Pattern(regexp = NamePatterns.NAME, message = "name " + NamePatterns.NAME_MESSAGE) String name) {
        adminService.deleteExchange(name);
    }

    // ---- bindings ----

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Une una cola a un exchange con una routing key")
    public BindingResponse createBinding(@Valid @RequestBody BindingRequest request) {
        return adminService.createBinding(request);
    }

    @DeleteMapping("/bindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quita el binding (parámetros de consulta: exchange, queue, routingKey)")
    public void deleteBinding(@Valid @ModelAttribute BindingRequest request) {
        adminService.deleteBinding(request);
    }
}
