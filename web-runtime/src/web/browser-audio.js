(function(global){
    'use strict';

    var root = document.documentElement;
    var state = global.__mindustryAudio || (global.__mindustryAudio = {
        ctx: null,
        buffers: new Map(),
        durations: new Map(),
        voices: new Map(),
        music: new Map(),
        nextVoice: 1,
        platformPaused: false,
        unlocked: false,
        installed: false
    });

    function setError(error){
        root.setAttribute('data-mindustry-audio', 'error');
        root.setAttribute('data-mindustry-audio-error', String(error).replace(/\s+/g, ' ').slice(0, 500));
    }

    function decode(url){
        var pending = state.buffers.get(url);
        if(pending) return pending;

        pending = fetch(url)
            .then(function(response){
                if(!response.ok){
                    throw new Error('Audio asset fetch failed (' + response.status + '): ' + url);
                }
                return response.arrayBuffer();
            })
            .then(function(bytes){
                return state.ctx.decodeAudioData(bytes);
            })
            .then(function(buffer){
                state.durations.set(url, buffer.duration || 0);
                return buffer;
            });

        state.buffers.set(url, pending);
        pending.catch(function(){ state.buffers.delete(url); });
        return pending;
    }

    function startVoice(voice, buffer){
        if(!state.voices.has(voice.id) || voice.started || state.platformPaused) return;

        var source = state.ctx.createBufferSource();
        var gain = state.ctx.createGain();
        var panner = state.ctx.createStereoPanner ? state.ctx.createStereoPanner() : null;

        source.buffer = buffer;
        source.loop = !!voice.loop;
        source.playbackRate.value = Math.max(0.01, voice.pitch);
        gain.gain.value = voice.paused ? 0 : voice.volume;
        if(panner) panner.pan.value = Math.max(-1, Math.min(1, voice.pan));

        source.connect(gain);
        if(panner){
            gain.connect(panner);
            panner.connect(state.ctx.destination);
        }else{
            gain.connect(state.ctx.destination);
        }

        voice.source = source;
        voice.gain = gain;
        voice.panner = panner;
        voice.started = true;

        source.onended = function(){
            var current = state.voices.get(voice.id);
            if(current === voice && !voice.loop) state.voices.delete(voice.id);
        };
        source.start(0);
    }

    function unlock(){
        if(!state.ctx) return;
        state.ctx.resume().then(function(){
            state.unlocked = true;
            root.setAttribute('data-mindustry-audio-unlocked', 'true');
            state.music.forEach(function(entry){
                if(entry.pendingPlay && !state.platformPaused){
                    entry.pendingPlay = false;
                    entry.element.play().catch(function(){ entry.pendingPlay = true; });
                }
            });
        }).catch(function(){});
    }

    function install(){
        if(state.installed) return !!state.ctx;
        state.installed = true;

        var AudioContextCtor = global.AudioContext || global.webkitAudioContext;
        if(!AudioContextCtor){
            root.setAttribute('data-mindustry-audio', 'unsupported');
            return false;
        }

        try{
            state.ctx = new AudioContextCtor();
        }catch(error){
            setError(error);
            return false;
        }

        state.decode = decode;
        state.startVoice = startVoice;
        ['pointerdown', 'touchstart', 'keydown'].forEach(function(type){
            global.addEventListener(type, unlock, {passive: true, capture: true});
        });

        root.setAttribute('data-mindustry-audio', 'installed');
        root.setAttribute('data-mindustry-audio-platform', 'running');
        return true;
    }

    function verify(url){
        if(!state.ctx){
            root.setAttribute('data-mindustry-audio', 'unsupported');
            return;
        }

        root.setAttribute('data-mindustry-audio', 'decoding');
        decode(url).then(function(buffer){
            if(!buffer || !(buffer.duration > 0)){
                throw new Error('Decoded audio has no duration: ' + url);
            }
            root.setAttribute('data-mindustry-audio', 'ready');
            root.setAttribute('data-mindustry-audio-smoke-ms', String(Math.round(buffer.duration * 1000)));
        }).catch(setError);
    }

    function playSound(url, volume, pitch, pan, loop){
        if(!state.ctx) return -1;

        var id = state.nextVoice++;
        var voice = {
            id: id,
            url: url,
            volume: volume,
            pitch: pitch,
            pan: pan,
            loop: loop,
            paused: false,
            started: false,
            source: null,
            gain: null,
            panner: null
        };

        state.voices.set(id, voice);
        decode(url).then(function(buffer){
            if(state.voices.get(id) === voice) startVoice(voice, buffer);
        }).catch(function(error){
            state.voices.delete(id);
            console.warn('Mindustry sound decode failed:', url, error);
        });
        return id;
    }

    function stopVoice(id){
        var voice = state.voices.get(id);
        if(!voice) return;
        try{ if(voice.source) voice.source.stop(); }catch(ignored){}
        state.voices.delete(id);
    }

    function stopSound(url){
        var ids = [];
        state.voices.forEach(function(voice, id){
            if(voice.url === url) ids.push(id);
        });
        ids.forEach(stopVoice);
    }

    function countSound(url){
        var count = 0;
        state.voices.forEach(function(voice){
            if(voice.url === url) count++;
        });
        return count;
    }

    function pauseVoice(id, paused){
        var voice = state.voices.get(id);
        if(!voice) return;
        voice.paused = paused;
        if(voice.gain) voice.gain.gain.value = paused ? 0 : voice.volume;
    }

    function loopVoice(id, looping){
        var voice = state.voices.get(id);
        if(!voice) return;
        voice.loop = looping;
        if(voice.source) voice.source.loop = looping;
    }

    function pitchVoice(id, pitch){
        var voice = state.voices.get(id);
        if(!voice) return;
        voice.pitch = pitch;
        if(voice.source) voice.source.playbackRate.value = pitch;
    }

    function volumeVoice(id, volume){
        var voice = state.voices.get(id);
        if(!voice) return;
        voice.volume = volume;
        if(voice.gain) voice.gain.gain.value = voice.paused ? 0 : volume;
    }

    function panVoice(id, pan){
        var voice = state.voices.get(id);
        if(!voice) return;
        voice.pan = pan;
        if(voice.panner) voice.panner.pan.value = pan;
    }

    function platformPause(paused){
        if(!state.ctx) return;
        state.platformPaused = paused;
        root.setAttribute('data-mindustry-audio-platform', paused ? 'paused' : 'running');
        root.setAttribute(paused ? 'data-mindustry-audio-pause-observed' : 'data-mindustry-audio-resume-observed', 'yes');

        if(paused){
            state.ctx.suspend().catch(function(){});
            state.music.forEach(function(entry){
                entry.resumeAfterPlatform = !entry.element.paused || entry.pendingPlay;
                entry.element.pause();
            });
            return;
        }

        if(state.unlocked) state.ctx.resume().catch(function(){});
        state.music.forEach(function(entry){
            if(!entry.resumeAfterPlatform) return;
            entry.resumeAfterPlatform = false;
            if(state.unlocked){
                entry.element.play().catch(function(){ entry.pendingPlay = true; });
            }else{
                entry.pendingPlay = true;
            }
        });

        state.voices.forEach(function(voice){
            if(voice.started) return;
            decode(voice.url).then(function(buffer){
                if(state.voices.get(voice.id) === voice) startVoice(voice, buffer);
            }).catch(function(){});
        });
    }

    function dispose(){
        var ids = [];
        state.voices.forEach(function(voice, id){ ids.push(id); });
        ids.forEach(stopVoice);
        state.voices.clear();

        state.music.forEach(function(entry){
            entry.element.pause();
            entry.element.removeAttribute('src');
            entry.element.load();
        });
        state.music.clear();

        if(state.ctx) state.ctx.close().catch(function(){});
        state.ctx = null;
        state.installed = false;
        root.setAttribute('data-mindustry-audio', 'disposed');
    }

    function createMusic(id, url){
        if(state.music.has(id)) return state.music.get(id);
        var element = new Audio();
        element.preload = 'none';
        element.src = url;
        element.playsInline = true;
        var entry = {
            element: element,
            pendingPlay: false,
            resumeAfterPlatform: false
        };
        state.music.set(id, entry);
        return entry;
    }

    function musicPrepare(id, url){
        createMusic(id, url);
    }

    function musicPlay(id, url, volume, pitch, pan, loop){
        var entry = createMusic(id, url);
        var element = entry.element;
        element.volume = volume;
        element.playbackRate = pitch;
        element.loop = loop;

        if(state.platformPaused || !state.unlocked){
            entry.pendingPlay = true;
            return;
        }

        element.play().then(function(){
            entry.pendingPlay = false;
        }).catch(function(){
            entry.pendingPlay = true;
        });
    }

    function musicPause(id, paused){
        var entry = state.music.get(id);
        if(!entry) return;
        if(paused){
            entry.element.pause();
            entry.pendingPlay = false;
        }else{
            entry.pendingPlay = true;
            if(state.unlocked && !state.platformPaused){
                entry.element.play().then(function(){
                    entry.pendingPlay = false;
                }).catch(function(){});
            }
        }
    }

    function musicStop(id){
        var entry = state.music.get(id);
        if(!entry) return;
        entry.element.pause();
        try{ entry.element.currentTime = 0; }catch(ignored){}
        entry.pendingPlay = false;
        entry.resumeAfterPlatform = false;
    }

    function musicDispose(id){
        var entry = state.music.get(id);
        if(!entry) return;
        entry.element.pause();
        entry.element.removeAttribute('src');
        entry.element.load();
        state.music.delete(id);
    }

    global.__mindustryAudioApi = {
        install: install,
        verify: verify,
        playSound: playSound,
        stopSound: stopSound,
        countSound: countSound,
        soundLength: function(url){ return state.durations.has(url) ? state.durations.get(url) : 0; },
        voicePlaying: function(id){ return state.voices.has(id); },
        stopVoice: stopVoice,
        pauseVoice: pauseVoice,
        loopVoice: loopVoice,
        pitchVoice: pitchVoice,
        volumeVoice: volumeVoice,
        panVoice: panVoice,
        activeVoiceCount: function(){ return state.voices.size; },
        platformPause: platformPause,
        dispose: dispose,
        musicPrepare: musicPrepare,
        musicPlay: musicPlay,
        musicPause: musicPause,
        musicStop: musicStop,
        musicPlaying: function(id){
            var entry = state.music.get(id);
            return !!(entry && !entry.element.paused && !entry.element.ended);
        },
        musicLoop: function(id, loop){
            var entry = state.music.get(id);
            if(entry) entry.element.loop = loop;
        },
        musicVolume: function(id, volume){
            var entry = state.music.get(id);
            if(entry) entry.element.volume = volume;
        },
        musicPositionSet: function(id, position){
            var entry = state.music.get(id);
            if(entry){
                try{ entry.element.currentTime = position; }catch(ignored){}
            }
        },
        musicPositionGet: function(id){
            var entry = state.music.get(id);
            return entry && isFinite(entry.element.currentTime) ? entry.element.currentTime : 0;
        },
        musicLength: function(id){
            var entry = state.music.get(id);
            return entry && isFinite(entry.element.duration) ? entry.element.duration : 0;
        },
        musicDispose: musicDispose
    };
})(window);
