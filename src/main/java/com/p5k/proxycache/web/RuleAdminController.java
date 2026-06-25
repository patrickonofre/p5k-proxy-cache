package com.p5k.proxycache.web;

import com.p5k.proxycache.rules.RuleAdminService;
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
@RequestMapping("/admin/rules")
public class RuleAdminController {

    private final RuleAdminService service;

    public RuleAdminController(RuleAdminService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CacheRuleResponse> create(@Valid @RequestBody CacheRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CacheRuleResponse.from(service.create(request)));
    }

    @GetMapping
    public List<CacheRuleResponse> list() {
        return service.list().stream().map(CacheRuleResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CacheRuleResponse get(@PathVariable Long id) {
        return CacheRuleResponse.from(service.get(id));
    }

    @PutMapping("/{id}")
    public CacheRuleResponse update(@PathVariable Long id, @Valid @RequestBody CacheRuleRequest request) {
        return CacheRuleResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
