package com.capstone.agenticrag.memory;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.agenticrag.common.PageResponse;
import com.capstone.agenticrag.memory.MemoryDtos.MemoryResponse;
import com.capstone.agenticrag.memory.MemoryDtos.MemorySettings;
import com.capstone.agenticrag.security.CurrentUser;
import com.capstone.agenticrag.security.UserPrincipal;

@RestController
@Tag(name = "Memory")
public class MemoryController {

    private final MemoryService memories;

    public MemoryController(MemoryService memories) {
        this.memories = memories;
    }

    @GetMapping("/api/memories")
    @Operation(summary = "List stored memories, most recently updated first")
    public PageResponse<MemoryResponse> list(@CurrentUser UserPrincipal me,
                                             @RequestParam(required = false) Memory.Category category,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "100") int size) {
        return memories.list(me.id(), category, page, size);
    }

    @PostMapping("/api/memories")
    @Operation(summary = "Add a memory manually")
    public ResponseEntity<MemoryResponse> create(@CurrentUser UserPrincipal me,
                                                 @Valid @RequestBody MemoryDtos.CreateMemoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memories.createManual(me.id(), req.content(), req.category()));
    }

    @PatchMapping("/api/memories/{id}")
    @Operation(summary = "Edit a memory's content or category")
    public MemoryResponse update(@CurrentUser UserPrincipal me, @PathVariable UUID id,
                                 @Valid @RequestBody MemoryDtos.UpdateMemoryRequest req) {
        return memories.update(me.id(), id, req);
    }

    @DeleteMapping("/api/memories/{id}")
    @Operation(summary = "Delete one memory")
    public ResponseEntity<Void> delete(@CurrentUser UserPrincipal me, @PathVariable UUID id) {
        memories.delete(me.id(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/memories")
    @Operation(summary = "Delete all of the user's memories")
    public MemoryDtos.ClearResult clear(@CurrentUser UserPrincipal me) {
        return new MemoryDtos.ClearResult(memories.clearAll(me.id()));
    }

    @GetMapping("/api/memory/settings")
    @Operation(summary = "Get memory settings")
    public MemorySettings settings(@CurrentUser UserPrincipal me) {
        return memories.settings(me.id());
    }

    @PatchMapping("/api/memory/settings")
    @Operation(summary = "Enable/disable memory and choose retention when conversations are deleted")
    public MemorySettings updateSettings(@CurrentUser UserPrincipal me,
                                         @RequestBody MemoryDtos.UpdateMemorySettingsRequest req) {
        return memories.updateSettings(me.id(), req);
    }
}
