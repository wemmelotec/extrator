(function () {
    /**
     * Marca no menu lateral a rota que corresponde a pagina atual.
     */
    function setActiveMenu() {
        var path = window.location.pathname;
        var key = "home";

        if (path.indexOf("/consulta/decisoes") === 0) {
            key = "consulta";
        } else if (path.indexOf("/documentos") === 0) {
            key = "documentos";
        }

        document.querySelectorAll(".nav-link").forEach(function (link) {
            if (link.getAttribute("data-nav") === key) {
                link.classList.add("active");
            } else {
                link.classList.remove("active");
            }
        });
    }

    /**
     * Registra o comportamento do botao que abre e recolhe a sidebar.
     */
    function wireSidebarToggle() {
        var toggle = document.getElementById("menuToggle");
        var sidebar = document.getElementById("sidebar");
        if (!toggle || !sidebar) {
            return;
        }

        toggle.textContent = "☰";

        toggle.addEventListener("click", function () {
            if (window.innerWidth <= 900) {
                sidebar.classList.toggle("open");
                return;
            }

            sidebar.classList.toggle("collapsed");
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        setActiveMenu();
        wireSidebarToggle();
    });
})();
