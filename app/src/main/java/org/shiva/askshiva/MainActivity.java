package org.shiva.askshiva;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IgnoreExtraProperties;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;
    private DatabaseReference mDatabaseRef = null;
    public LinkedHashMap<String, Question> mQuestions = new LinkedHashMap<>();
    public ArrayList<PlaceholderFragment> mFragments = new ArrayList<>();
    public static String mUserName;
    public static final String DATE_PATTERN = "MMM dd, hh:mm a";
    private static final String CHANNEL_ID = "org.shiva.askshiva.NOTIF_CHANNEL";
    private static final String JAMBOTTLE_RESOURCES = "https://git.io/vNOe6";
    private static final String QUESTION_PAPERS = "https://drive.google.com/open?id=1ejXS3lvzpsKNmUd0ehHbW1TVVC8grjNk";
    private static final String EXTRA_STARTUP_TAB = "org.shiva.askshiva.STARTUP_TAB";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = findViewById(R.id.tabs);

        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(mViewPager));
        int startup_tab = getIntent().getIntExtra(EXTRA_STARTUP_TAB, -1);
        if (startup_tab != -1 && tabLayout.getTabAt(startup_tab) != null)
            tabLayout.getTabAt(startup_tab).select();

        mSectionsPagerAdapter.startUpdate(mViewPager);
        for (int i = 0; i < 3; i++) {
            mFragments.add((PlaceholderFragment) mSectionsPagerAdapter.instantiateItem(mViewPager, i));
        }
        mSectionsPagerAdapter.finishUpdate(mViewPager);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askQuestion();
            }
        });
        configDatabase();
        createNotificationChannel();

        SharedPreferences pref = this.getSharedPreferences(IntroActivity.USER_NAME, Context.MODE_PRIVATE);
        mUserName = pref.getString(IntroActivity.USER_NAME, "");
    }

    private void configDatabase() {
        mDatabaseRef = FirebaseDatabase.getInstance().getReference();
        mDatabaseRef.keepSynced(true);

        ChildEventListener childEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                String key = dataSnapshot.getKey();
                Question question = dataSnapshot.getValue(Question.class);

                if (question != null) {
                    question.setKey(key);
                    mQuestions.put(key, question);
                    findViewById(R.id.progress_bar).setVisibility(View.GONE);
                    updateCorrectTab(question, "add");
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                String key = dataSnapshot.getKey();
                Question newQuestion = dataSnapshot.getValue(Question.class);
                Question oldQuestion = mQuestions.get(key);

                if (newQuestion != null) {
                    newQuestion.setKey(key);
                    if (newQuestion.date != null && !newQuestion.date.equals(oldQuestion.date))
                        return;
                    if (oldQuestion.answer.isEmpty() && !newQuestion.answer.isEmpty()) {
                        mFragments.get(0).updateQnsList(oldQuestion, "remove");
                        mFragments.get(1).updateQnsList(newQuestion, "add");
                        oldQuestion.copyFields(newQuestion);
                        showNotification(key.hashCode(), getString(R.string.new_answer), newQuestion.question_text, 1);
                        return;
                    }
                    updateCorrectTab(newQuestion, "update");
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                String key = dataSnapshot.getKey();
                Question question = mQuestions.get(key);

                if (question != null) {
                    updateCorrectTab(question, "remove");
                    mQuestions.remove(key);
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {}

            private void updateCorrectTab(@NonNull Question q, String action) {
                int tab_index;

                if ("old".equals(q.date))
                    tab_index = 2;
                else if (q.answer.isEmpty())
                    tab_index = 0;
                else
                    tab_index = 1;
                mFragments.get(tab_index).updateQnsList(q, action);
            }
        };
        mDatabaseRef.child("questions").addChildEventListener(childEventListener);
    }

    @IgnoreExtraProperties
    public static class Question {
        public String asker;
        public String date;
        public String question_text;
        public Map<String, Boolean> upvotes;
        public String answer;
        private String key;

        public Question(String asker, String question_text) {
            this.asker = asker;
            this.question_text = question_text;
            this.upvotes = new HashMap<>();
            this.upvotes.put(asker, true);
            this.answer = "";

            DateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN, Locale.US);
            Date date = new Date();
            this.date = dateFormat.format(date);
        }
        private Question() {}

        public String getAsker() { return asker; }
        public String getDate() { return date; }
        public String getQuestion_text() { return question_text; }
        public String getAnswer() { return answer; }
        public Map<String, Boolean> getUpvotes() { return upvotes; }
        @Exclude
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public void copyFields(Question q) {
            this.asker = q.asker;
            this.date = q.date;
            this.question_text = q.question_text;
            this.upvotes = q.upvotes;
            this.answer = q.answer;
        }
    }

    private void askQuestion() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.question_dialog, null);
        builder.setView(dialogView);

        builder.setPositiveButton(R.string.action_ask, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {}
        });
        builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        final AlertDialog dialog = builder.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String question_text =
                        ((EditText) dialogView.findViewById(R.id.question_i)).getText().toString();
                if (question_text.matches("^\\w[\\w\\s]*")) {
                    DatabaseReference postsRef = mDatabaseRef.child("questions");
                    postsRef.push().setValue(new Question(MainActivity.mUserName, question_text));
                    dialog.dismiss();
                    ((TabLayout) findViewById(R.id.tabs)).getTabAt(0).select();
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.error_regex_unmatch), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notif_channel_name);
            String description = getString(R.string.notif_channel_desc);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void showNotification(int notif_id, String text_title, String text_content, int startup_tab) {
        Intent open_main = new Intent(this, MainActivity.class);
        if (startup_tab != -1) {
            open_main.putExtra(EXTRA_STARTUP_TAB, startup_tab);
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, open_main, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_check_circle_24dp)
                .setContentTitle(text_title)
                .setContentText(text_content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManagerCompat notif_manager = NotificationManagerCompat.from(this);
        notif_manager.notify(notif_id, builder.build());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_jb_res:
                openWebsite(JAMBOTTLE_RESOURCES);
                return true;
            case R.id.action_get_qp:
                openWebsite(QUESTION_PAPERS);
                return true;
            case R.id.action_about:
                AlertDialog alertDialog = new AlertDialog.Builder(this)
                        .setMessage(R.string.main_about)
                        .setCancelable(true)
                        .create();
                alertDialog.show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openWebsite(String  url) {
        Intent open_website = new Intent(Intent.ACTION_VIEW);
        open_website.setData(Uri.parse(url));
        startActivity(open_website);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        private ArrayList<Question> mQuestionsList = new ArrayList<>();
        private ArrayAdapter<Question> mListAdapter;
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "org.shiva.askshiva.SECTION_NUMBER";
        public static final String EXTRA_ANSWER_TEXT = "org.shiva.askshiva.ANSWER_TEXT";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            ListView qListview = rootView.findViewById(R.id.questions_l);
            mListAdapter = setCustomAdapter(qListview, mQuestionsList);
            return rootView;
        }

        private ArrayAdapter<Question> setCustomAdapter(ListView listview, final ArrayList<Question> array_list) {
            ArrayAdapter<Question> list_adapter =
                    new ArrayAdapter<Question>(getContext(), R.layout.question_card, array_list) {
                        @Override @NonNull
                        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                            if (convertView == null) {
                                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
                                convertView = inflater.inflate(R.layout.question_card, parent, false);
                            }
                            Question q = array_list.get(position);

                            if ("old".equals(q.date)) {
                                ((TextView) convertView.findViewById(R.id.question_info)).setText(R.string.jambottle_res);
                                convertView.findViewById(R.id.upvote_icon).setVisibility(View.GONE);
                                convertView.findViewById(R.id.upvotes).setVisibility(View.GONE);
                            } else {
                                String asker = q.asker.equals(MainActivity.mUserName) ? "You" : q.asker;
                                final String q_info = asker + " asked " + formatDate(q.date);
                                ((TextView) convertView.findViewById(R.id.question_info)).setText(q_info);
                                ((TextView) convertView.findViewById(R.id.upvotes)).setText(String.valueOf(q.upvotes.size() - 1));
                            }
                            ((TextView) convertView.findViewById(R.id.question_text)).setText(q.question_text);
                            if (!q.answer.isEmpty()) {
                                ((ImageView) convertView.findViewById(R.id.status_icon)).setImageResource(R.drawable.ic_check_circle_24dp);
                            }
                            return convertView;
                        }
                    };
            listview.setAdapter(list_adapter);

            listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    showQuestionOptions((Question) parent.getItemAtPosition(position));
                }
            });
            return list_adapter;
        }

        private String formatDate(String date_str) {
            try {
                DateFormat date_format = new SimpleDateFormat(DATE_PATTERN, Locale.US);
                Calendar q_date = Calendar.getInstance();
                q_date.setTime(date_format.parse(date_str));
                int diff_days = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) - q_date.get(Calendar.DAY_OF_YEAR);

                if (diff_days == 0)
                    return "at " + date_str.substring(date_str.indexOf(',') + 2);
                else if (diff_days == 1)
                    return "Yesterday";
                else
                    return "on " + date_str.substring(0, date_str.indexOf(','));
            } catch (Exception e) {
                return "on " + date_str;
            }
        }

        private void showQuestionOptions(final Question q) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            LayoutInflater inflater = this.getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.question_options, null);
            View upvoteBtn = dialogView.findViewById(R.id.upvote_btn);
            View deleteBtn = dialogView.findViewById(R.id.delete_btn);
            View viewAnswerBtn = dialogView.findViewById(R.id.view_answer_btn);
            final DatabaseReference db = FirebaseDatabase.getInstance().getReference().child("questions");

            if (q.answer.isEmpty()) {
                viewAnswerBtn.setVisibility(View.GONE);

                if (MainActivity.mUserName.equals(q.asker))
                    upvoteBtn.setVisibility(View.GONE);
                else
                    deleteBtn.setVisibility(View.GONE);
            } else {
                upvoteBtn.setVisibility(View.GONE);
                deleteBtn.setVisibility(View.GONE);
            }
            builder.setView(dialogView);
            final AlertDialog dialog = builder.show();

            upvoteBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    db.child(q.getKey()).child("upvotes").child(MainActivity.mUserName).setValue(true);
                    dialog.dismiss();
                }
            });
            deleteBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    db.child(q.getKey()).setValue(null);
                    dialog.dismiss();
                }
            });
            viewAnswerBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent open_viewer = new Intent(getContext(), ViewerActivity.class);
                    open_viewer.putExtra(EXTRA_ANSWER_TEXT, q.answer);
                    ((NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE)).cancel(q.getKey().hashCode());
                    dialog.dismiss();
                    startActivity(open_viewer);
                }
            });
        }

        public void updateQnsList(Question question, String action) {
            switch (action) {
                case "add":
                    mQuestionsList.add(0, question);
                    break;
                case "remove":
                    mQuestionsList.remove(question);
                    break;
                case "update":
                    for (Question q: mQuestionsList) {
                        if (q.getKey().equals(question.getKey())) {
                            q.copyFields(question);
                            break;
                        }
                    }
                    break;
            }
            mListAdapter.notifyDataSetChanged();
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return 3;
        }
    }
}
