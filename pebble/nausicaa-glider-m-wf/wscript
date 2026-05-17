import os
try:
    from pebble_sdk import *
except ImportError:
    pass

def options(ctx):
    ctx.load('pebble_sdk')

def configure(ctx):
    ctx.load('pebble_sdk')

def build(ctx):
    ctx.load('pebble_sdk')
    ctx.pbl_program(source=ctx.path.ant_glob('src/c/**/*.c'), target='app.elf')
    ctx.pbl_bundle(elf='app.elf', js=ctx.path.ant_glob('src/pkjs/**/*.js'))
    
    