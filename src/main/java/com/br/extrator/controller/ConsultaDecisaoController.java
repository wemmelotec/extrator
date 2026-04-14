package com.br.extrator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ConsultaDecisaoController {

    @GetMapping({"/", "/consulta/decisoes"})
    public String telaConsulta() {
        return "consulta-decisoes";
    }
}
