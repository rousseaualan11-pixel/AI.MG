// ==========================================
// 1. CONFIGURATION & CLÉ API
// ==========================================
let GEMINI_API_KEY = localStorage.getItem('gemini_api_key');
const GEMINI_URL = () => `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${localStorage.getItem('gemini_api_key')}`;

let extractedText = "";

window.askNewKey = () => {
    const key = prompt("🔑 Collez votre clé API Gemini (commençant par AIza) :");
    if (key && key.trim().length > 10) {
        localStorage.setItem('gemini_api_key', key.trim());
        alert("✅ Clé enregistrée !");
        location.reload();
    }
};

// Si aucune clé n'est présente au démarrage
if (!GEMINI_API_KEY) {
    setTimeout(() => { if(!localStorage.getItem('gemini_api_key')) askNewKey(); }, 1500);
}

function updateBar(id, percId, value) {
    const bar = document.getElementById(id);
    const text = document.getElementById(percId);
    if (bar) bar.style.width = value + "%";
    if (text) text.innerText = value + "%";
}

// ==========================================
// 2. GESTION DU FICHIER (OPTIMISÉ MOBILE)
// ==========================================
window.handleFileUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    const labelText = document.getElementById('label-text');
    if(labelText) labelText.innerText = "📄 " + file.name.substring(0, 15) + "...";

    document.getElementById('upload-status-container').classList.remove('hidden');
    updateBar('upload-fill', 'upload-perc', 10);

    try {
        if (file.name.toLowerCase().endsWith('.pdf')) {
            extractedText = await extractPDF(file);
        } else {
            extractedText = await extractWord(file);
        }
        
        updateBar('upload-fill', 'upload-perc', 100);
        const btnAi = document.getElementById('btn-ai');
        btnAi.disabled = false;
        btnAi.innerText = "🚀 ANALYSER LE COURS";
        btnAi.style.background = "#6366f1";
    } catch (err) {
        alert("Erreur de lecture du fichier.");
    }
};

// ==========================================
// 3. ANALYSE IA AVEC PROGRESSION RÉELLE (1%...2%...)
// ==========================================
window.processCourse = async () => {
    if (!extractedText) return;
    if (!localStorage.getItem('gemini_api_key')) { askNewKey(); return; }

    document.getElementById('ia-detail-container').classList.remove('hidden');
    document.getElementById('btn-ai').classList.add('hidden');
    
    // Animation de progression (environ 15 secondes)
    let progress = 0;
    const timerText = document.getElementById('timer-text');
    const interval = setInterval(() => {
        if (progress < 95) {
            progress++;
            updateBar('ia-fill', 'ia-perc', progress);
            if(timerText) timerText.innerText = `⏳ Patience... ~${Math.ceil((100-progress)/6)}s restantes`;
        }
    }, 150);

    const promptText = `Analyse ce cours. Donne un titre, un résumé structuré et un quiz de 3 questions. 
    Réponds uniquement en JSON : {"titre":"", "sections":[{"n":"", "c":""}], "quiz":[{"q":"", "correct":"", "wrong":[]}]}
    Texte : ${extractedText.substring(0, 15000)}`;

    try {
        const response = await fetch(GEMINI_URL(), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ contents: [{ parts: [{ text: promptText }] }] })
        });

        const data = await response.json();
        clearInterval(interval);

        if (data.error) throw new Error(data.error.message);

        updateBar('ia-fill', 'ia-perc', 100);
        const rawText = data.candidates[0].content.parts[0].text;
        const json = JSON.parse(rawText.substring(rawText.indexOf('{'), rawText.lastIndexOf('}') + 1));

        renderResults(json);
        document.getElementById('btn-result').classList.remove('hidden');
        if(timerText) timerText.innerText = "✨ Analyse terminée !";

    } catch (err) {
        clearInterval(interval);
        alert("Erreur : " + err.message);
        document.getElementById('btn-ai').classList.remove('hidden');
    }
};

// ==========================================
// 4. MOTEURS ET ONGLET
// ==========================================
async function extractPDF(file) {
    pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
    const ab = await file.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data: ab }).promise;
    let t = "";
    for (let i = 1; i <= pdf.numPages; i++) {
        const p = await pdf.getPage(i);
        const c = await p.getTextContent();
        t += c.items.map(it => it.str).join(" ") + " ";
    }
    return t;
}

async function extractWord(file) {
    const ab = await file.arrayBuffer();
    const r = await mammoth.extractRawText({ arrayBuffer: ab });
    return r.value;
}

window.switchTab = (type) => {
    const isSum = type === 'sum';
    document.getElementById('summary-content').classList.toggle('hidden', !isSum);
    document.getElementById('quiz-content').classList.toggle('hidden', isSum);
    document.getElementById('tab-sum').style.background = isSum ? '#6366f1' : '#334155';
    document.getElementById('tab-quiz').style.background = isSum ? '#334155' : '#6366f1';
};

function renderResults(data) {
    let html = `<h2 style="color:#4ade80;">${data.titre}</h2>`;
    data.sections.forEach(s => {
        html += `<b style="color:#818cf8;display:block;margin-top:15px;">📍 ${s.n}</b><p>${s.c}</p>`;
    });
    document.getElementById('summary-result').innerHTML = html;

    let qHtml = "<h3>Quiz</h3>";
    data.quiz.forEach(q => {
        qHtml += `<div class="quiz-card" style="background:#1e293b;padding:10px;margin-bottom:10px;border-radius:10px;">
            <p><b>${q.q}</b></p><p style="color:#4ade80;">✅ ${q.correct}</p>
        </div>`;
    });
    document.getElementById('quiz-result').innerHTML = qHtml;
}

window.showResults = () => { document.getElementById('results-container').classList.remove('hidden'); };
