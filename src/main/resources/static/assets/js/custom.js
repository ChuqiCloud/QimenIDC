(function () {

// perfect scrollbar
const initPerfectScrollbar = () => {
    const container = document.querySelectorAll('.perfect-scrollbar');
    for (let i = 0; i < container.length; i++) {
      new PerfectScrollbar(container[i], {
        wheelPropagation: true,
        // suppressScrollX: true,
      });
    }
  };
  initPerfectScrollbar();

document.addEventListener("alpine:init", () => {
    Alpine.data("collapse", () => ({
        collapse: false,

        collapseSidebar() {
            this.collapse = !this.collapse;
        },
    }));
    Alpine.data("dropdown", (initialOpenState = false) => ({
        open: initialOpenState,

        toggle() {
            this.open = !this.open;
        },
    }));
    Alpine.data("modals", (initialOpenState = false) => ({
        open: initialOpenState,

        toggle() {
            this.open = !this.open;
        },
    }));

    // main - custom functions
    Alpine.data("main", (value) => ({}));

    Alpine.store("app", {
        // Light and dark Mode
        mode: Alpine.$persist('light'),
            toggleMode(val) {
            if (!val) {
            val = this.mode || 'light'; // light And Dark
            }

            this.mode = val;
        },

        // sidebar
        sidebar: false,
        toggleSidebar() {
            this.sidebar = !this.sidebar;
        },

        fullscreen: false,
        toggleFullScreen() {
            if (document.fullscreenElement) {
                document.exitFullscreen();
            } else {
                document.documentElement.requestFullscreen();
            }
        },
        
    });
});

})();