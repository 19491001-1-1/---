package com.assessment.packing.web.api;

import com.assessment.packing.web.dto.SolveDtos;
import com.assessment.packing.web.service.SolveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solve")
public final class SolveController {
    private final SolveService solveService;

    public SolveController(SolveService solveService) {
        this.solveService = solveService;
    }

    @PostMapping
    public SolveDtos.Result solve(@Valid @RequestBody SolveDtos.Request request) {
        return solveService.solve(request);
    }
}
