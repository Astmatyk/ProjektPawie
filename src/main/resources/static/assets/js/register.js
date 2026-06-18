//register.js

const form = document.getElementById("registerForm");
const errorMessage = document.getElementById("jsCode");

// wacky workaround na nieodświeżanie strony w mikrosekundę od kliknięcia submit
form.addEventListener("submit", (event) => {
    event.preventDefault();

    const emailVal = document.getElementById("email").value;
    const loginVal = document.getElementById("username").value;
    const passwordVal = document.getElementById("password").value;
    const passwordConfirmVal = document.getElementById("passwordConfirm").value;

    errorMessage.innerHTML = `
            <div class="spinner-container">
                <div class="spinner"></div>
            </div>
        `;

    // pobieranie wartości CSRF z tagów meta
    const token = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const header = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    // dorzucanie nagłówka CSRF
    fetch("/api/register", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            [header]: token // dynamiczny klucz nagłówka
        },
        body: JSON.stringify({ email: emailVal, username: loginVal, password: passwordVal, passwordConfirm: passwordConfirmVal })
    })
        .then(async (res) => {
            const data = await res.json();

            if (res.ok) {
                // udało sie
                errorMessage.style.color = "#FFFFFF";
                errorMessage.innerText = "Sukces! Przekierowywanie...";
                errorMessage.style.display = "block";

                // po 1.5 sekundy kierujemy na login
                setTimeout(() => {
                    window.location.href = "/login";
                }, 1500);
            } else {
                // coś poszło nie tak
                errorMessage.style.color = "#ff5252"; // czerwony
                errorMessage.innerText = data.error || "Błąd podczas rejestracji. Spróbuj ponownie.";
                errorMessage.style.display = "block";
            }
        })
        .catch((err) => {
            // nie ma nawet komunikacji
            errorMessage.style.color = "#ff5252";
            errorMessage.innerText = "Błąd połączenia z serwerem. Spróbuj ponownie.";
            errorMessage.style.display = "block";
        });
});