document.addEventListener("DOMContentLoaded", () => {
	// Alert close buttons
	const closeButtons = document.querySelectorAll("[data-alert-close]");
	closeButtons.forEach((btn) => {
		btn.addEventListener("click", () => {
			const alert = btn.closest(".alert");
			if (alert) {
				alert.style.display = "none";
			}
		});
	});

	// Confirm-before-submit forms
	const confirmForms = document.querySelectorAll("form[data-confirm]");
	confirmForms.forEach((form) => {
		form.addEventListener("submit", (event) => {
			const message = form.getAttribute("data-confirm");
			if (message && !window.confirm(message)) {
				event.preventDefault();
			}
		});
	});

	// Sidebar toggle (mobile)
	const appLayout = document.querySelector(".app-layout");
	const toggleBtns = document.querySelectorAll("[data-sidebar-toggle]");
	const overlay = document.querySelector("[data-sidebar-overlay]");

	function openSidebar() {
		if (appLayout) appLayout.classList.add("sidebar-open");
	}

	function closeSidebar() {
		if (appLayout) appLayout.classList.remove("sidebar-open");
	}

	toggleBtns.forEach((btn) => {
		btn.addEventListener("click", () => {
			if (appLayout && appLayout.classList.contains("sidebar-open")) {
				closeSidebar();
			} else {
				openSidebar();
			}
		});
	});

	if (overlay) {
		overlay.addEventListener("click", closeSidebar);
	}
});
