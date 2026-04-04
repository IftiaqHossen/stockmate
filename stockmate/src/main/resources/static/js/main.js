document.addEventListener("DOMContentLoaded", () => {
	const closeButtons = document.querySelectorAll("[data-alert-close]");
	closeButtons.forEach((btn) => {
		btn.addEventListener("click", () => {
			const alert = btn.closest(".alert");
			if (alert) {
				alert.style.display = "none";
			}
		});
	});

	const confirmForms = document.querySelectorAll("form[data-confirm]");
	confirmForms.forEach((form) => {
		form.addEventListener("submit", (event) => {
			const message = form.getAttribute("data-confirm");
			if (message && !window.confirm(message)) {
				event.preventDefault();
			}
		});
	});
});
