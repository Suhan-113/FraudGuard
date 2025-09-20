const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const SpeechSDK = require('microsoft-cognitiveservices-speech-sdk');
const twilio = require('twilio');
const fetch = require('node-fetch');

// --- CONFIGURATION ---
const TWILIO_ACCOUNT_SID = 'ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxx';
const TWILIO_AUTH_TOKEN = 'cxxxxxxxxxxxxxxxxxxxxxxxxxxxxx';
const TWILIO_PHONE_NUMBER = '+1xxxxxxxxxx';
const AZURE_KEY = "3xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
const AZURE_REGION = "cxxxxxxx";
const PYTHON_SERVER_URL = 'http://python-server:5001/predict';
const FRAUD_THRESHOLD = 0.8;

const speechConfig = SpeechSDK.SpeechConfig.fromSubscription(AZURE_KEY, AZURE_REGION);
speechConfig.speechRecognitionLanguage = "en-US";
const twilioClient = twilio(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);

let androidAppWs = null;

// --- HELPER FUNCTION ---
const ulaw_decode = (ulaw_byte) => {
    ulaw_byte = ~ulaw_byte;
    let exponent = (ulaw_byte & 0x70) >> 4;
    let mantissa = ulaw_byte & 0x0f;
    let pcm_val = (mantissa << 3) + 0x84;
    pcm_val <<= exponent - 1;
    return (ulaw_byte & 0x80) ? -pcm_val : pcm_val;
};

// --- SERVER SETUP ---
const app = express();
const port = 3000;
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

app.use(express.json());

// --- WebSocket logic for the LIVE CALL analysis feature ---
wss.on('connection', (ws) => {
    console.log('Node.js: A WebSocket connection was established.');
    
    let recognizer = null;
    let pushStream = null;

    ws.on('message', async (message) => {
        try {
            const msg = JSON.parse(message);

            if (msg.event === 'connected') {
                console.log('Node.js: A client connected.');
                return;
            }

            if (msg.type === 'app_connect') {
                console.log('Node.js: Android app client identified.');
                androidAppWs = ws;
                ws.on('close', () => {
                    console.log('Node.js: Android app disconnected.');
                    androidAppWs = null;
                });
                return;
            }

            if (msg.event === 'start') {
                console.log('Node.js: Twilio media stream started.');
                
                // --- THIS IS THE NEWLY ADDED BLOCK ---
                // When the stream starts, we know the merge was successful.
                // Send a confirmation message back to the Android app.
                if (androidAppWs && androidAppWs.readyState === WebSocket.OPEN) {
                    androidAppWs.send(JSON.stringify({ type: 'merge_successful' }));
                    console.log('Node.js: Sent merge confirmation to the app.');
                }
                // --- END OF NEW BLOCK ---
                
                const audioFormat = SpeechSDK.AudioStreamFormat.getWaveFormatPCM(8000, 16, 1);
                pushStream = SpeechSDK.AudioInputStream.createPushStream(audioFormat);
                const audioConfig = SpeechSDK.AudioConfig.fromStreamInput(pushStream);
                recognizer = new SpeechSDK.SpeechRecognizer(speechConfig, audioConfig);

                recognizer.recognizing = async (s, e) => {
                    const transcript = e.result.text;
                    if (transcript && transcript.length > 10) {
                        console.log(`Node.js (Call): RECOGNIZING: ${transcript}`);
                        try {
                            const response = await fetch(PYTHON_SERVER_URL, {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ text: transcript })
                            });

                            if (!response.ok) {
                                const errorData = await response.json();
                                throw new Error(`Python server error: ${errorData.error || response.statusText}`);
                            }

                            const data = await response.json();
                            console.log(`Node.js (Call): Got fraud score from Python: ${data.score.toFixed(4)}`);

                            if (data.score > FRAUD_THRESHOLD) {
                                console.log(`Node.js (Call): !!! FRAUD DETECTED !!!`);
                                const alertPayload = { type: "fraud_alert", reason: `High risk activity detected in call.` };
                                
                                if (androidAppWs && androidAppWs.readyState === WebSocket.OPEN) {
                                    androidAppWs.send(JSON.stringify(alertPayload));
                                    console.log('Node.js (Call): Alert sent to Android app.');
                                } else {
                                    console.log('Node.js (Call): Android app not connected, cannot send alert.');
                                }
                            }
                        } catch (error) {
                            console.error("Node.js (Call): An error occurred during the prediction process:", error);
                        }
                    }
                };
                recognizer.startContinuousRecognitionAsync();
            }

            if (msg.event === 'media') {
                if (recognizer && pushStream) {
                    const mulaw_buffer = Buffer.from(msg.media.payload, 'base64');
                    const pcm_buffer = Buffer.alloc(mulaw_buffer.length * 2);
                    for (let i = 0; i < mulaw_buffer.length; i++) {
                        pcm_buffer.writeInt16LE(ulaw_decode(mulaw_buffer[i]), i * 2);
                    }
                    pushStream.write(pcm_buffer);
                }
            }
        } catch (err) {
            console.log("Received a non-JSON message, likely from Twilio's audio stream. Ignoring.");
        }
    });

    ws.on('close', () => {
        console.log('Node.js: A WebSocket connection was closed.');
        if (recognizer) {
            recognizer.stopContinuousRecognitionAsync();
        }
    });
});

// --- The existing Twilio Webhook endpoint for the LIVE CALL feature ---
app.post('/handle-call', (req, res) => {
    console.log('Node.js: Webhook received for a call.');
    const twiml = new twilio.twiml.VoiceResponse();
    const host = req.headers.host;
    twiml.start().stream({ url: `wss://${host}/` });
    twiml.say("Your call is now being protected.");
    twiml.pause({ length: 600 });
    res.type('text/xml');
    res.send(twiml.toString());
});

// --- PROXY ENDPOINT FOR THE MESSAGING APP FEATURE ---
app.post('/predict', async (req, res) => {
    console.log("Node.js (Message): Received request body:", req.body);
    const { text } = req.body;

    if (!text) {
        return res.status(400).json({ error: 'Text is required' });
    }

    console.log(`Node.js (Message): Received text from app: "${text}"`);

    try {
        const pythonResponse = await fetch(PYTHON_SERVER_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ text: text })
        });

        if (!pythonResponse.ok) {
            const errorData = await pythonResponse.json();
            throw new Error(`Python server error: ${errorData.error || pythonResponse.statusText}`);
        }

        const data = await pythonResponse.json();
        console.log(`Node.js (Message): Got score from Python: ${data.score}`);
        res.status(200).json(data);
    } catch (error) {
        console.error("Node.js (Message): Error proxying to Python server:", error);
        res.status(500).json({ error: 'Failed to get prediction' });
    }
});

server.listen(port, () => {
    console.log(`Node.js: Server is running on http://localhost:${port}`);
});