package com.br.extrator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ConsultaDecisaoController {

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/consulta/decisoes")
    public String telaConsulta() {
        return "consulta-decisoes";
    }

    @GetMapping("/documentos")
    public String telaDocumentos() {
        return "documentos";
    }
}
