package com.br.extrator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ConsultaDecisaoController {

    /**
     * Exibe a tela de login da aplicacao.
     *
     * @return nome da view Thymeleaf de login
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /**
     * Exibe a pagina inicial da aplicacao.
     *
     * @return nome da view Thymeleaf da home
     */
    @GetMapping("/")
    public String home() {
        return "home";
    }

    /**
     * Exibe a tela de consulta de decisoes.
     *
     * @return nome da view Thymeleaf da consulta
     */
    @GetMapping("/consulta/decisoes")
    public String telaConsulta() {
        return "consulta-decisoes";
    }

    /**
     * Exibe a tela de gerenciamento de documentos.
     *
     * @return nome da view Thymeleaf de documentos
     */
    @GetMapping("/documentos")
    public String telaDocumentos() {
        return "documentos";
    }
}
