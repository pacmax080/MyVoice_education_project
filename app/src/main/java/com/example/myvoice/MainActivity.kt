package com.example.myvoice

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.HashMap
import kotlin.text.StringBuilder


class MainActivity : AppCompatActivity() {

    // initialization values class/ инициализация переменных
    lateinit var requestInput: TextInputEditText
    lateinit var podsAdapter: SimpleAdapter
    lateinit var progressBar: ProgressBar
    lateinit var waEngine: WAEngine
    val TAG: String = "MainActivity"
    val pods = mutableListOf<HashMap<String, String>>()
    lateinit var textToSpeech: TextToSpeech
    var ttsReady: Boolean = false
    val VOICE_RECOGNITION_REQUEST_CODE: Int = 808

    //Main
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initWalframeEngene()
        initTts()
    }

    // function initialization widgets/ метод инициализации виджетов
    fun initViews() {

        //init tool bar
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        //init text request input/ инициализация поля для ввода текстового запроса
        requestInput = findViewById(R.id.text_input_edit)
        requestInput.setOnEditorActionListener { v, androidID, event ->
            if (androidID == EditorInfo.IME_ACTION_DONE){
                pods.clear()
                podsAdapter.notifyDataSetChanged()
                val quest = requestInput.text.toString()
                checkRequest(quest)
            }
            return@setOnEditorActionListener false
        }

        //init listView
        val podsList: ListView = findViewById(R.id.pods_list)
        podsAdapter = SimpleAdapter(
            applicationContext,
            pods,
            R.layout.item_pod,
            arrayOf("Title", "Content"),
            intArrayOf(R.id.title, R.id.content)
        )
        podsList.adapter = podsAdapter
        podsList.setOnItemClickListener { parent, view, position, id ->
            if (ttsReady) {
                val title = pods[position]["Title"]
                val content = pods[position]["Content"]
                textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, null, title)
            }
        }

        // init Floating Action Button
        val floatButton: FloatingActionButton = findViewById(R.id.voice_input_button)
        floatButton.setOnClickListener {
            pods.clear()
            podsAdapter.notifyDataSetChanged()

            if (ttsReady){
                textToSpeech.stop()
            }

            showVoiceInitDialog()
        }

        //init Progress Bar
        progressBar = findViewById(R.id.progress_bar)
    }

    // initialization menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    // action menu/
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            //action for stop button
            R.id.action_stop -> {
                if (ttsReady){
                    textToSpeech.stop()
                }
                return true
            }
            //action for clear button
            R.id.action_clear -> {
                requestInput.text?.clear()
                pods.clear()
                podsAdapter.notifyDataSetChanged()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // init WA API
    fun initWalframeEngene() {
        waEngine = WAEngine().apply {
            appID = "JR8JWP-6P66W6T6LH"
            addFormat("plaintext")
        }
    }

    // SnackBar
    fun showSnackBar(meassege: String) {
        Snackbar.make(findViewById(android.R.id.content), meassege, Snackbar.LENGTH_INDEFINITE)
            .apply {
                setAction(android.R.string.ok) {
                    dismiss()
                }
                show()
            }
    }

    // request to server
    fun asWolfram(request: String) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val query = waEngine.createQuery().apply {
                input = request
            }
            runCatching {
                waEngine.performQuery(query)
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (result.isError) {
                        showSnackBar(result.errorMessage)
                        return@withContext// обработать ответ
                    }
                    if (!result.isSuccess) {
                        requestInput.error = getString(R.string.error_do_not_understand)
                    }
                    for (pod in result.pods) {
                        if (pod.isError) continue
                        val content = StringBuilder()
                        for (subpod in pod.subpods) {
                            for (element in subpod.contents) {
                                if (element is WAPlainText) {
                                    content.append(element.text)
                                }
                            }
                        }
                        pods.add(0, HashMap<String, String>().apply {
                            put("Title", pod.title)
                            put("Content", content.toString())
                        })
                    }
                    podsAdapter.notifyDataSetChanged()
                }
            }.onFailure { t ->
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showSnackBar(
                        t.message ?: getString(R.string.error_something_went_wrong)
                    )// обработка ошибкаи
                }
            }
        }
    }

    // init text to speech
    fun initTts () {
        textToSpeech = TextToSpeech(this) {code ->
            if (code != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS eror code: $code")
                showSnackBar(getString(R.string.error_tts_is_not_ready))
            } else{
                ttsReady = true
            }
        }
        textToSpeech.language = Locale.getDefault()
    }

    // init voice dialog
    fun showVoiceInitDialog () {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.request_hint))
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        runCatching {
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE)
        }.onFailure {t->
            showSnackBar(t.message ?: getString(R.string.error_voice_recognition_unavailable))
        }
    }

    // Activation result voice dialog
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK){
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)?.let {question ->
                requestInput.setText(question)
                checkRequest(question)
            }
        }
    }

    // My additional check request
    fun checkRequest (question: String){
        // fun checking if a substring is included in a string
        fun instanceOf (substring: String, str: String): Boolean {
            return str.indexOf(substring, 0, true) != -1
        }
        // fun checking on Cyrillic symbols
        fun checkCirilic (str: String): Boolean {
            var check: Boolean = false
            for (i in str.indices) {
                if (Character.UnicodeBlock.of(str[i]) == Character.UnicodeBlock.CYRILLIC) {
                    check = true// contains Cyrillic/
                }
            }
            return check
        }
        if ( instanceOf("Кто", question) && instanceOf("Разработчик", question)){
            pods.add(0, HashMap<String, String>().apply {
                put("Title", "Разработчик")
                put("Content", "Разработчик этого приложения Ступин Максим")
            })
            podsAdapter.notifyDataSetChanged()
        } else if (checkCirilic(question)) {
            pods.add(0, HashMap<String, String>().apply {
                put("Title", "Русский текст")
                put("Content", "На данный момент я отвечаю на вопросы только на английском языке")
            })
            podsAdapter.notifyDataSetChanged()
        } else {
            asWolfram(question)
        }
    }
}
