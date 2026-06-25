package com.p5k.proxycache.web;

import com.p5k.proxycache.rules.ApplicationAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/applications")
public class ApplicationAdminController {

    private final ApplicationAdminService service;

    public ApplicationAdminController(ApplicationAdminService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationResponse.from(service.create(request)));
    }

    @GetMapping
    public List<ApplicationResponse> list() {
        return service.list().stream().map(ApplicationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable Long id) {
        return ApplicationResponse.from(service.get(id));
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable Long id, @Valid @RequestBody ApplicationRequest request) {
        return ApplicationResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
