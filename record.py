stream = p.open(format=FORMAT,
                channels=CHANNELS,
                rate=RATE,
                input=True,
                frames_per_buffer=CHUNK,
                input_device_index=YOUR_STEREO_MIX_INDEX) # <--- Use the index you found here
