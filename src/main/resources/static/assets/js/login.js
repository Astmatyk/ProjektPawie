//login.js

const form = document.getElementById('loginForm');
const errorMessage = document.getElementById('jsCode');

form.addEventListener("submit", async function(event) {
    // form.innerHTML = "";
    errorMessage.innerHTML = `
      <div class="spinner-container">
          <div class="spinner"></div>
      </div>
    `;
});