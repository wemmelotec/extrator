package com.br.extrator.controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.br.extrator.service.ExtratorService;

import net.sourceforge.tess4j.TesseractException;

@RestController
@RequestMapping("/ler")
public class ExtratorController {
	
	@Autowired
	private ExtratorService extratorService;
	
	@PostMapping
	public ResponseEntity<String> extrair(@RequestPart MultipartFile file) throws IOException, TesseractException{
		
		return ResponseEntity.ok(extratorService.extrair(file));
		
	}

}
	