package com.marlabs.support.controller;

import com.marlabs.support.batch.BatchManifest;
import com.marlabs.support.batch.BatchService;
import com.marlabs.support.caller.CallerIdentity;
import com.marlabs.support.caller.CallerRegistry;
import com.marlabs.support.exception.InvalidRequestException;
import com.marlabs.support.exception.UnauthorizedCallerException;
import com.marlabs.support.model.BatchResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class BatchController {

    private final CallerRegistry callerRegistry;
    private final BatchService batchService;

    public BatchController(CallerRegistry callerRegistry, BatchService batchService) {
        this.callerRegistry = callerRegistry;
        this.batchService = batchService;
    }

    @PostMapping(value = "/batches", consumes = "multipart/form-data")
    public BatchResponse submitBatch(
            @RequestHeader(value = "X-Caller-Id", required = false) String callerId,
            @RequestPart("metadata") BatchManifest manifest,
            @RequestParam("files") List<MultipartFile> files) {
        CallerIdentity caller = callerRegistry.lookup(callerId)
                .orElseThrow(() -> new UnauthorizedCallerException("UNKNOWN_CALLER", "missing or unknown X-Caller-Id"));

        Map<String, byte[]> filesByName = new LinkedHashMap<>();
        for (MultipartFile file : files) {
            filesByName.put(file.getOriginalFilename(), readBytes(file));
        }

        return batchService.processBatch(caller, manifest, filesByName);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidRequestException("UNREADABLE_UPLOAD", "could not read uploaded file: " + file.getOriginalFilename());
        }
    }
}
