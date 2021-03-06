package pong.flow;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES20;
import android.util.Log;

public class FlowInteraction {
	private static final String TAG = "FlowInteraction";
	public static final float MAX_DIS_RATE =  0.06f;

	public static final float MAX_BRUSH_SIZE = .35f;
	
	public static float disipationRate = 0.03f;
	public static float brushStroke = 0.075f;
	public static float pressureStroke = 0.075f;
	public static int[] fb, depthRb, renderTex;
	IntBuffer texBuffer;
	Context _context;
	int width, height;
	Shader inputShader;
	private FloatBuffer _vertexBuffer;
	float[] newinput = {0,0};
	float[] inputDir = {0,0};
	float[] phoneRot = {1.0f, 0.5f, 0.0f};
	float [] compresedRot = {1.0f, 0.5f, 0.0f};
	float[] prevInput = newinput;
	public static boolean Clear = true;
	float _lifeTime = 5.0f; //seconds
	float _burstDecrement = 0.0f;
	float _totalTime = 0.0f;
	float _deltaTime = 0.0f;
	boolean alternate = true;
	float _ratio;
	FlowSurface surfaceRef;
	

	private SharedPreferences _prefs;

	public FlowInteraction(Context cont,int w, int h , SharedPreferences sp, FlowSurface _surfaceRef) {
		width = w; 
		height = h;
		_context = cont;
		inputShader = new Shader(_context, R.raw.input_vs, R.raw.input_ps);

		float _width = (float) w / 100.0f;
		float _height = (float) h / 100.0f;
		_ratio = _width / _height;
		_width = _ratio * 5.0f;
		_height = 5.0f;
		
		surfaceRef = _surfaceRef;

		// bottom left
		Vec3 p1 = new Vec3(-_width, _height, 0);
		// bottom right
		Vec3 p2 = new Vec3(_width, _height, 0);
		// top left
		Vec3 p3 = new Vec3(-_width, -_height, 0);
		// top left
		Vec3 p4 = new Vec3(-_width, -_height, 0);
		// top right
		Vec3 p5 = new Vec3(_width, -_height, 0);
		// bottom right
		Vec3 p6 = new Vec3(_width, _height, 0);

		// face 1
		Vec3 v1 = p2.Minus(p1);
		Vec3 v2 = p3.Minus(p1);
		Vec3 norm1 = v1.Cross(v2);
		norm1.Normalize();

		// face2
		Vec3 v3 = p4.Minus(p5);
		Vec3 v4 = p6.Minus(p5);
		Vec3 norm2 = v3.Cross(v4);
		norm2.Normalize();

		Vec3 avgNorm = norm1.Plus(norm2);
		avgNorm.Normalize();

		float moneyCoords[] = { // locations
		p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z, p4.x, p4.y, p4.z,
				p5.x, p5.y, p5.z, p6.x,
				p6.y,
				p6.z,
				// normals
				norm1.x, norm1.y, norm1.z, avgNorm.x, avgNorm.y, avgNorm.z,
				avgNorm.x, avgNorm.y, avgNorm.z, avgNorm.x, avgNorm.y,
				avgNorm.z, norm2.x, norm2.y, norm2.z, avgNorm.x, avgNorm.y,
				avgNorm.z,
				// tex coord.
				0,1, 1,1, 0,0, 0,0, 1,0, 1,1};

		ByteBuffer vbb = ByteBuffer.allocateDirect(

		// (# of coordinate values * 4 bytes per float)
				moneyCoords.length * 4);

		vbb.order(ByteOrder.nativeOrder());// use the device hardware's native
											// byte order
		_vertexBuffer = vbb.asFloatBuffer(); // create a floating point buffer
		// from the ByteBuffer
		_vertexBuffer.put(moneyCoords); // add the coordinates to the
		// FloatBuffer
		_vertexBuffer.position(0); // set the buffer to read the first
		
		_prefs = sp;
		
		
		initPrefs();
		// coordinate
	}
	
	private void initPrefs()
	{
		
		float val = _prefs.getFloat("input_dis_preference", (FlowLiveWallpaperService.DEFAULT_THEME.inputDis));
		setDisRate(val*MAX_DIS_RATE);
		
		 val = _prefs.getFloat("brushstroke_preference", (float) .5);
		 setBrushSize(val*MAX_BRUSH_SIZE);
		
	}

	public void Update(float deltaTime)
	{
		_burstDecrement = 0.0f;
		_deltaTime = deltaTime;
		_totalTime += _deltaTime;
		
		if(_totalTime >= 0.05f)
		{
			_totalTime = 0.0f;
			_burstDecrement = 1.0f;
		}
	}
	
	public void UpdateRotation(float[] rot)
	{
		phoneRot[0] = Math.max(-1.0f, Math.min(phoneRot[0] - rot[0], 1.0f));
		phoneRot[1] = Math.max(-1.0f, Math.min(phoneRot[1] + rot[1], 1.0f));
		float x = (phoneRot[0] / 2.0f) + 0.5f;
		float y = (phoneRot[1] / 2.0f) + 0.5f;
		
		if(x <= 0.5f && 0.5f - x < 0.15f) x = 0.35f;
		if(x > 0.5f && x - 0.5f  < 0.15f) x = 0.65f;
		if(y <= 0.5f &&  0.5f - y < 0.15f) y = 0.35f;
		if(y > 0.5f &&  y - 0.5f < 0.15f) y = 0.65f;
		
		compresedRot[0] = x;
		compresedRot[1] = y;
		//if(rot[1] <= 0) rot[1] = 0.0f;
		//else rot[1] = 1.0f;
		//x -> 0.9 <-> -0.9 
		//Log.i("Accelerom", rot[0] + " " + rot[1] + " " + rot[2]);
	}
	
	public void UpdateInput(float x, float y, float xDir, float yDir, float mag)
	{
		//y += 0.075f;
		//Log.i(TAG, "x: " + x + " y: " + y);
		//map from 0.5-> 1.0
		float[] n_input = {x, y};
		newinput = n_input;
		if(mag != 0)
		{
			float[] dir_in = {xDir, yDir};
			inputDir = dir_in;
		}
	}

	public void DrawInput(float[] _uMVPMatrix) {
		GLES20.glViewport(0, 0, width, height);

		// Bind the framebuffer
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0]);

		// specify texture as color attachment
		GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
				GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
				renderTex[0], 0);

		// check status
		int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
		if (status != GLES20.GL_FRAMEBUFFER_COMPLETE)
			Log.e("Post Process Manager", "Frame buffer error 0");
		

		GLES20.glClearColor(1.0f,0.5f,0.0f,1.0f);
		
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		
		int _program = inputShader.getProgram();
		GLES20.glUseProgram(_program);
		FlowRenderer.checkGlError("glUseProgram");
		
		GLES20.glViewport(0, 0, width, height);
		int mvpLoc = GLES20.glGetUniformLocation(_program, "uMVPMatrix");
		GLES20.glUniformMatrix4fv(mvpLoc, 1, false, _uMVPMatrix, 0);
		
		if(Clear)
		{
			GLES20.glUniform1f(GLES20.glGetUniformLocation(_program, "clear"), 1.0f);
			Clear = false; 
		}
		else
		{
			GLES20.glUniform1f(GLES20.glGetUniformLocation(_program, "clear"), 0.0f);
		}
		GLES20.glUniform1f(GLES20.glGetUniformLocation(_program, "clock"), _burstDecrement);
		
		GLES20.glUniform1f(GLES20.glGetUniformLocation(_program, "disipationRate"), disipationRate);
		 
		GLES20.glUniform1f(GLES20.glGetUniformLocation(_program, "brushStroke"), pressureStroke);
		
		GLES20.glUniform1f(GLES20.glGetUniformLocation(_program, "width"), width);
		
		GLES20.glUniform1f(GLES20.glGetUniformLocation(_program, "height"), height);
		
		GLES20.glUniform1f(GLES20.glGetUniformLocation(_program, "screenRatio"), _ratio);
		if(alternate)
		{
			GLES20.glUniform1f(GLES20.glGetUniformLocation(_program, "alternate"), 1.0f);
			alternate = !alternate;
		}
		else
		{
			GLES20.glUniform1f(GLES20.glGetUniformLocation(_program, "alternate"), 0.0f);
			alternate = !alternate;
		}
		
		if(prevInput[0] == newinput[0] && prevInput[1] == newinput[1])
		{
			newinput[0] = -100.0f;
			newinput[1] = -100.0f;
			
		}
		GLES20.glUniform3fv(GLES20.glGetUniformLocation(_program, "phoneRotation"), 1, compresedRot, 0);
		GLES20.glUniform2fv(GLES20.glGetUniformLocation(_program, "touch"), 1, newinput, 0);
		GLES20.glUniform2fv(GLES20.glGetUniformLocation(_program, "dir"), 1, inputDir, 0);
		// usingTheme
		prevInput = newinput;
		int _flowLoc = GLES20.glGetUniformLocation(_program, "flowMap");	
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTex[0]);
		GLES20.glUniform1i(_flowLoc, 0);
		int _flowThemeLoc = GLES20.glGetUniformLocation(_program, "themeFlowMap");	
		GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, surfaceRef.getFlowMap());
		GLES20.glUniform1i(_flowThemeLoc, 1);
		
		_vertexBuffer.position(0);
		GLES20.glVertexAttribPointer(
				GLES20.glGetAttribLocation(_program, "aPosition"), 3,
				GLES20.GL_FLOAT, false, 0, _vertexBuffer);
		GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(_program,
				"aPosition"));
		_vertexBuffer.position(36);
		GLES20.glVertexAttribPointer(
				GLES20.glGetAttribLocation(_program, "textureCoord"), 2,
				GLES20.GL_FLOAT, false, 0, _vertexBuffer);
		GLES20.glEnableVertexAttribArray(GLES20.glGetAttribLocation(
				_program, "textureCoord"));
		GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
		FlowRenderer.checkGlError("glDrawArrays");
	}

	public void initInteraction() {
		fb = new int[1];
		depthRb = new int[1];
		renderTex = new int[1];
		GLES20.glGenFramebuffers(1, fb, 0);

		int texW = width / 2;// (width / 2);
		int texH = height /2;// (height / 4);

		GLES20.glGenTextures(1, renderTex, 0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTex[0]);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
				GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
				GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
				GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
				GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		int[] buf = new int[texW * texH];
		texBuffer = ByteBuffer.allocateDirect(buf.length * 4)
				.order(ByteOrder.nativeOrder()).asIntBuffer();
		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, texW, texH,
				0, GLES20.GL_RGBA, GLES20. GL_UNSIGNED_BYTE, texBuffer);
	}
	
	public void setDisRate(float f)
	{
		this.disipationRate = f;
		
	}

	public void setBrushSize(float f)
	{
		// TODO Auto-generated method stub
		this.brushStroke = f;
	}
}
